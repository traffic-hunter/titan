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

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traffichunter.titan.core.message.Message;
import org.traffichunter.titan.core.util.Assert;
import org.traffichunter.titan.core.util.Handler;
import org.traffichunter.titan.core.util.Destination;
import org.traffichunter.titan.dispatch.exporter.DispatchExporter;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Base {@link DispatchGateway} for platform and virtual-thread executors.
 *
 * <p>The gateway owns the executor and manages startup and shutdown. Its handlers do the dispatch
 * work: {@link RouteDispatchChainHandler} admits messages to destination queues, and
 * {@link FanoutDispatchChainHandler} manages destination consumers and fanout. Custom handlers
 * run between them.</p>
 *
 * <p>The fanout handler deletes queues and manages their consumers. Queue creation uses the
 * dispatcher registry directly.</p>
 *
 * <pre>{@code
 * sparkDispatch(message)
 *      |
 *      v
 * DispatchHandlerChain
 *      |
 *      v
 * RouteDispatchChainHandler -> DispatcherQueue(destination).enqueue(message)
 *      |
 *      v
 * optional middle handlers (backup, metrics, ...)
 *      |
 *      v
 * FanoutDispatchChainHandler -> computeIfAbsent(destination, consume)
 * }</pre>
 *
 * @author yun
 */
abstract class AbstractExecutorDispatchGateway implements DispatchGateway {

    private static final Logger log = LoggerFactory.getLogger(AbstractExecutorDispatchGateway.class);
    private static final long SHUTDOWN_TIMEOUT_SECONDS = 60;

    private final ExecutorService executor;
    private final Dispatcher dispatcher;
    private final FanoutDispatchChainHandler fanoutHandler;
    private final AtomicBoolean closed = new AtomicBoolean();
    private DispatchHandlerChain handlerChain;

    protected AbstractExecutorDispatchGateway(
            ExecutorService executor,
            DispatchExporter exporter,
            Dispatcher dispatcher
    ) {
        this.executor = executor;
        this.dispatcher = dispatcher;
        this.fanoutHandler = new FanoutDispatchChainHandler(executor, exporter, dispatcher);
        this.handlerChain = DispatchHandlerChain.chain(executor)
                .add(new RouteDispatchChainHandler(dispatcher))
                .add(fanoutHandler);
    }

    @Override
    public DispatchGateway chainHandler(Handler<DispatchHandlerChain> chainHandler) {
        DispatchHandlerChain chain = DispatchHandlerChain.chain(executor);
        chain.add(new RouteDispatchChainHandler(dispatcher));
        chainHandler.handle(chain);
        chain.add(fanoutHandler);
        this.handlerChain = chain;
        return this;
    }

    @Override
    public CompletableFuture<@Nullable Void> sparkDispatch(Message message) {
        Assert.checkNotNull(message, "message");

        if (closed.get()) {
            throw new IllegalStateException("DispatchGateway is closed");
        }

        return handlerChain.sparkDispatch(new DispatchContext(message))
                .thenApply(ignored -> null);
    }

    @Override
    public boolean isOpen() {
        return !closed.get();
    }

    @Override
    public boolean isClosed() {
        return closed.get();
    }

    /**
     * Creates a dispatcher queue through the gateway-owned dispatcher.
     *
     * <p>Queue creation is idempotent. If the queue already exists, the
     * existing instance is returned and the supplied byte limit is ignored.</p>
     */
    @Override
    public DispatcherQueue createQueue(Destination destination, long maxPendingBytes) {
        if (closed.get()) {
            throw new IllegalStateException("DispatchGateway is closed");
        }

        return dispatcher.getOrPut(destination, maxPendingBytes);
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
        if (closed.get()) {
            throw new IllegalStateException("DispatchGateway is closed");
        }
        return fanoutHandler.deleteQueue(destination, force);
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            fanoutHandler.close();
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
            handlerChain.clear();
        }
    }
}
