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
package org.traffichunter.titan.dispatch;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traffichunter.titan.core.message.Message;
import org.traffichunter.titan.core.util.Destination;
import org.traffichunter.titan.core.util.management.DispatcherQueueMbeans;
import org.traffichunter.titan.dispatch.exporter.DispatchExporter;

/**
 * Owns destination consumer registration, execution, and removal after messages are routed.
 *
 * <p>At most one long-lived consumer is registered per destination. The handler starts that task
 * without awaiting its completion because dispatch completion only represents successful
 * queue admission and consumer activation. Queue deletion and handler shutdown cancel registered
 * consumers and let their polling loops observe the corresponding lifecycle state.</p>
 *
 * @author yun
 */
final class FanoutDispatchChainHandler implements DispatchChainHandler {

    private static final Logger log = LoggerFactory.getLogger(FanoutDispatchChainHandler.class);

    private final Map<Destination, CompletableFuture<@Nullable Void>> consumers = new ConcurrentHashMap<>();
    private final Set<DispatcherQueue> deletedQueues = ConcurrentHashMap.newKeySet();
    private final ExecutorService executor;
    private final DispatchExporter exporter;
    private final Dispatcher dispatcher;
    private final AtomicBoolean closed = new AtomicBoolean();

    FanoutDispatchChainHandler(
            ExecutorService executor,
            DispatchExporter exporter,
            Dispatcher dispatcher
    ) {
        this.executor = executor;
        this.exporter = exporter;
        this.dispatcher = dispatcher;
    }

    @Override
    public DispatchChain handle(DispatchContext context, DispatchChain chain) {
        Message message = context.getMessage();
        fanout(message.getDestination());
        return chain.next(context);
    }

    CompletableFuture<@Nullable Void> fanout(Destination destination) {
        if (closed.get()) {
            throw new IllegalStateException("Fanout dispatch handler is closed");
        }
        return consumers.computeIfAbsent(destination, this::consume);
    }

    DispatcherQueueDeleteResult deleteQueue(Destination destination, boolean force) {
        if (closed.get()) {
            throw new IllegalStateException("Fanout dispatch handler is closed");
        }

        DispatcherQueue queue = dispatcher.get(destination);
        if (queue == null) {
            return DispatcherQueueDeleteResult.notFound();
        }
        int size = queue.size();
        if (size > 0 && !force) {
            return DispatcherQueueDeleteResult.notEmpty(size);
        }
        if (force) {
            queue.clear();
        }

        deletedQueues.add(queue);
        CompletableFuture<@Nullable Void> consumer = consumers.remove(destination);
        if (consumer != null) {
            consumer.cancel(true);
        }
        dispatcher.remove(destination);
        DispatcherQueueMbeans.unregister(queue.getDestination());
        return DispatcherQueueDeleteResult.deleted(size);
    }

    void close() {
        if (closed.compareAndSet(false, true)) {
            consumers.values().forEach(future -> future.cancel(true));
            consumers.clear();
        }
    }

    private CompletableFuture<@Nullable Void> consume(Destination destination) {
        DispatcherQueue queue = dispatcher.getOrPut(destination);
        log.info("Starting fanout consumer for destination={}", destination.path());

        CompletableFuture<@Nullable Void> result = new CompletableFuture<>();
        executor.execute(() -> {
            try {
                while (!closed.get()
                        && !Thread.currentThread().isInterrupted()
                        && !deletedQueues.contains(queue)) {
                    try {
                        Message message = queue.dispatch(1, TimeUnit.SECONDS);
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
                        if (closed.get() || executor.isShutdown()) {
                            break;
                        }
                    }
                }
                result.complete(null);
            } catch (Exception e) {
                result.completeExceptionally(e);
            } finally {
                deletedQueues.remove(queue);
                consumers.remove(destination, result);
            }
        });
        return result;
    }
}
