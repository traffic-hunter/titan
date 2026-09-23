/*
 * Copyright 2025 traffic-hunter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
    public DispatcherQueue createQueue(String group, Destination destination, long maxPendingBytes) {
        if (closed.get()) {
            throw new IllegalStateException("DispatchGateway is closed");
        }

        return dispatcher.getOrPut(group, destination, maxPendingBytes);
    }

    /**
     * Deletes a dispatcher queue and detaches its consumer.
     *
     * <p>The queue is closed before it leaves the dispatcher, so a producer admitted in the
     * meantime is refused outright rather than filling a queue this call is about to drop.
     * Non-empty queues are rejected unless force deletion is requested.</p>
     */
    @Override
    public DispatcherQueueDeleteResult deleteQueue(String group, Destination destination, boolean force) {
        DispatcherQueue queue = getQueue(group, destination);
        if (queue == null) {
            return DispatcherQueueDeleteResult.notFound();
        }

        int size = queue.size();
        if (size > 0 && !force) {
            return DispatcherQueueDeleteResult.notEmpty(size);
        }

        // While the queue is still open and registered, its consumer is unambiguously its own.
        fanoutHandler.detach(queue);
        queue.close();

        // Remove the queue this call looked up, never a replacement created since. The
        // dispatcher unregisters the MBean of whatever it actually removed.
        if (!dispatcher.remove(queue)) {
            return DispatcherQueueDeleteResult.notFound();
        }
        if (force) {
            queue.clear();
        }
        return DispatcherQueueDeleteResult.deleted(size);
    }

    /**
     * Manually pauses the queue without detaching its consumer.
     *
     * <p>A manual pause blocks both enqueue and dispatch, so producers stop
     * being admitted and queued messages stop being delivered. The consumer
     * stays attached and waits until the queue is resumed. To discard the
     * queued messages instead, use {@link #purgeQueue(String, Destination)}.</p>
     */
    @Override
    public boolean pauseQueue(String group, Destination destination) {
        DispatcherQueue queue = getQueue(group, destination);
        if (queue == null) {
            return false;
        }
        queue.pause();
        return true;
    }

    /**
     * Clears the manual pause so the queue admits and delivers messages again.
     *
     * <p>A queue that is still over its byte limit stays paused for producers
     * by flow control, because the queue re-evaluates its own pause state
     * rather than trusting the caller. A pressure pause never blocks dispatch,
     * so consumers can drain the queue back below the resume threshold.</p>
     */
    @Override
    public boolean resumeQueue(String group, Destination destination) {
        DispatcherQueue queue = getQueue(group, destination);
        if (queue == null) {
            return false;
        }
        queue.resume();
        return true;
    }

    /**
     * Drops every pending message while keeping the queue and its consumer.
     */
    @Override
    public boolean purgeQueue(String group, Destination destination) {
        DispatcherQueue queue = getQueue(group, destination);
        if (queue == null) {
            return false;
        }
        queue.clear();
        return true;
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

    /** Looks the queue up inside its own group. An unknown group is not created. */
    private @Nullable DispatcherQueue getQueue(String group, Destination destination) {
        if (closed.get()) {
            throw new IllegalStateException("DispatchGateway is closed");
        }

        return dispatcher.get(group, destination);
    }
}
