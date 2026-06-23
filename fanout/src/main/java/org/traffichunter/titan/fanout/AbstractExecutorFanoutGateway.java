/*
The MIT License

Copyright (c) 2025 traffic-hunter

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
*/
package org.traffichunter.titan.fanout;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traffichunter.titan.core.message.Message;
import org.traffichunter.titan.core.message.dispatcher.Dispatcher;
import org.traffichunter.titan.core.message.dispatcher.DispatcherQueue;
import org.traffichunter.titan.core.message.dispatcher.DispatcherQueueDeleteResult;
import org.traffichunter.titan.core.util.Assert;
import org.traffichunter.titan.core.util.Handler;
import org.traffichunter.titan.core.util.concurrent.Damper;
import org.traffichunter.titan.core.util.Destination;
import org.traffichunter.titan.core.util.concurrent.NoopDamper;
import org.traffichunter.titan.core.util.mbeans.DispatcherQueueMbeans;
import org.traffichunter.titan.fanout.exporter.FanoutExporter;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Executor-backed {@link FanoutGateway} implementation shared by platform and
 * virtual-thread variants.
 *
 * <p>Each destination has at most one active consumer task in {@link #consumers}.
 * Producer calls are processed through a {@link FanoutHandlerChain}. The gateway
 * always installs routing as the first handler and dispatch as the last handler,
 * while optional middle handlers can add behavior such as backup without being
 * embedded in the core fanout flow.</p>
 *
 * <p>Routing enqueues the message into the dispatcher queue. Dispatch starts the
 * destination consumer if needed, and that consumer drains the queue sequentially
 * into the configured {@link FanoutExporter}. This gives a simple fanout invariant:
 * ordering is preserved per destination queue, while different destinations can
 * progress independently on the executor.</p>
 *
 * <pre>{@code
 * publish(message)
 *      |
 *      v
 * FanoutHandlerChain
 *      |
 *      v
 * RouteFanoutHandler -> DispatcherQueue(destination).enqueue(message)
 *      |
 *      v
 * optional middle handlers (backup, metrics, ...)
 *      |
 *      v
 * DispatchFanoutHandler -> fanout(destination) -> computeIfAbsent(destination, consume)
 *      |
 *      v
 * consume loop -> dispatcherQueue.dispatch() -> exporter.export(...)
 * }</pre>
 *
 * <p>The optional {@link Damper} is a small back-pressure hook for executor
 * implementations that can create many concurrent tasks. The virtual-thread
 * gateway uses it to cap active fanout dispatch work.</p>
 *
 * @author yun
 */
abstract class AbstractExecutorFanoutGateway implements FanoutGateway {

    private static final Logger log = LoggerFactory.getLogger(AbstractExecutorFanoutGateway.class);
    private static final long SHUTDOWN_TIMEOUT_SECONDS = 10;

    private final Map<Destination, Future<?>> consumers = new ConcurrentHashMap<>();
    private final Set<DispatcherQueue> deletedQueues = ConcurrentHashMap.newKeySet();

    private final ExecutorService executor;
    private final FanoutExporter exporter;
    private final Dispatcher dispatcher;
    private final AtomicBoolean isClosed = new AtomicBoolean();
    private final Damper damper;

    private FanoutHandlerChain fanoutHandlerChain;

    protected AbstractExecutorFanoutGateway(
            ExecutorService executor,
            FanoutExporter exporter,
            Dispatcher dispatcher
    ) {
        this(executor, exporter, dispatcher, NoopDamper.getInstance());
    }

    protected AbstractExecutorFanoutGateway(
            ExecutorService executor,
            FanoutExporter exporter,
            Dispatcher dispatcher,
            Damper damper
    ) {
        this.executor = executor;
        this.exporter = exporter;
        this.dispatcher = dispatcher;
        this.damper = damper;
        this.fanoutHandlerChain = FanoutHandlerChain.chain()
                .add(new RouteFanoutHandler(executor, this::route))
                .add(new DispatchFanoutHandler(this::fanout));
    }

    @Override
    public FanoutGateway chainHandler(Handler<FanoutHandlerChain> chainHandler) {
        FanoutHandlerChain chain = FanoutHandlerChain.chain();
        chain.add(new RouteFanoutHandler(executor, this::route));
        chainHandler.handle(chain);
        chain.add(new DispatchFanoutHandler(this::fanout));
        this.fanoutHandlerChain = chain;
        return this;
    }

    @Override
    public List<Future<@Nullable Void>> fanout(Collection<Destination> destinations) {
        if (isClosed.get()) {
            throw new IllegalStateException("FanoutGateway is closed");
        }

        return destinations.stream()
                .map(this::fanout)
                .toList();
    }

    @SuppressWarnings("unchecked")
    @Override
    public Future<@Nullable Void> fanout(Destination destination) {
        if (isClosed.get()) {
            throw new IllegalStateException("FanoutGateway is closed");
        }

        return (Future<@Nullable Void>) consumers.computeIfAbsent(destination, this::consume);
    }

    @Override
    public List<Future<@Nullable Void>> publish(Collection<Message> messages) {
        if (isClosed.get()) {
            throw new IllegalStateException("FanoutGateway is closed");
        }

        return messages.stream()
                .map(this::publish)
                .toList();
    }

    @Override
    public Future<@Nullable Void> publish(Message message) {
        Assert.checkNotNull(message, "message");

        if (isClosed.get()) {
            throw new IllegalStateException("FanoutGateway is closed");
        }

        return fanoutHandlerChain.next(new FanoutContext(message));
    }

    @Override
    public boolean isOpen() {
        return !isClosed.get();
    }

    @Override
    public boolean isClosed() {
        return isClosed.get();
    }

    @Override
    public void close() {
        if (isClosed.compareAndSet(false, true)) {
            consumers.values().forEach(future -> future.cancel(true));
            consumers.clear();
            executor.shutdown();
            try {
                if (!executor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                    if (!executor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                        log.warn("Fanout executor did not terminate cleanly");
                    }
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            try {
                fanoutHandlerChain.close();
            } catch (Exception e) {
                log.warn("Failed to close fanout handler chain", e);
            }
        }
    }

    /**
     * Creates a dispatcher queue through the gateway-owned dispatcher.
     *
     * <p>Queue creation is idempotent. If the queue already exists, the
     * existing instance is returned and the supplied capacity is ignored.</p>
     */
    @Override
    public DispatcherQueue createQueue(Destination destination, int capacity) {
        if (isClosed.get()) {
            throw new IllegalStateException("FanoutGateway is closed");
        }

        return dispatcher.getOrPut(destination, capacity);
    }

    /**
     * Deletes a dispatcher queue and detaches its consumer.
     *
     * <p>Deletion removes the queue from the dispatcher, unregisters its JMX
     * MBean, and marks the current queue instance as deleted so a running
     * consumer can exit. Non-empty queues are rejected unless force deletion is
     * requested.</p>
     */
    @Override
    public DispatcherQueueDeleteResult deleteQueue(Destination destination, boolean force) {
        DispatcherQueue queue = dispatcher.get(destination);
        if (queue == null) {
            return new DispatcherQueueDeleteResult(DispatcherQueueDeleteResult.Status.NOT_FOUND, 0);
        }
        int size = queue.size();
        if (size > 0 && !force) {
            return new DispatcherQueueDeleteResult(DispatcherQueueDeleteResult.Status.NOT_EMPTY, size);
        }
        if (force) {
            queue.clear();
        }

        deletedQueues.add(queue);
        Future<?> consumer = consumers.remove(destination);
        if (consumer != null) {
            consumer.cancel(true);
        }
        dispatcher.remove(destination);
        DispatcherQueueMbeans.unregister(queue.getDestination());
        return new DispatcherQueueDeleteResult(DispatcherQueueDeleteResult.Status.DELETED, size);
    }

    /**
     * Runs one destination consumer.
     *
     * <p>The loop polls with a timeout instead of blocking indefinitely so it
     * can observe queue deletion and shutdown state. The returned future uses
     * {@code @Nullable Void} because successful completion is represented by a
     * {@code null} value.</p>
     */
    protected CompletableFuture<@Nullable Void> consume(Destination destination) {
        DispatcherQueue dispatcherQueue = dispatcher.getOrPut(destination);
        log.info("Starting fanout consumer for destination={}", destination.path());

        CompletableFuture<@Nullable Void> result = new CompletableFuture<>();
        executor.execute(() -> {
            try {
                while (!isClosed()
                        && !Thread.currentThread().isInterrupted()
                        && !deletedQueues.contains(dispatcherQueue)) {
                    damper.acquire();
                    try {
                        Message message = dispatcherQueue.dispatch(1, TimeUnit.SECONDS);
                        if (message == null) {
                            continue;
                        }

                        exporter.export(destination, message);
                    } catch (InterruptedException e) {
                        log.error("Interrupted while waiting for message to be delivered", e);
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        log.error("Unexpected error while dispatching message", e);
                        if (isClosed() || executor.isShutdown()) {
                            break;
                        }
                    } finally {
                        damper.release();
                    }
                }
                result.complete(null);
            } catch (Exception e) {
                result.completeExceptionally(e);
            } finally {
                deletedQueues.remove(dispatcherQueue);
                consumers.remove(destination, result);
            }
        });
        return result;
    }

    protected @Nullable Message route(Message message) {
        Destination destination = message.getDestination();
        Assert.checkNotNull(destination, "message.destination");

        DispatcherQueue dq = dispatcher.getOrPut(destination);

        try {
            dq.enqueue(message);
            return message;
        } catch (Exception e) {
            log.warn(
                    "Failed to route fanout message. destination={}",
                    destination.path(),
                    e
            );
            if(dq.contains(message)) {
                dq.remove(message);
            }
            return null;
        }
    }
}
