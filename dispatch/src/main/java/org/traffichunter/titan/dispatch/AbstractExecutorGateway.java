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
import org.traffichunter.titan.dispatch.exporter.DispatchExporter;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Base {@link Gateway} for platform and virtual-thread executors.
 *
 * <p>The gateway owns the executor and manages startup and shutdown. Its handler chain ends at
 * the queue: {@link RouteDispatchChainHandler} admits messages to destination queues, and custom
 * handlers run after it.</p>
 *
 * <p>The {@link Dispatcher} owns the queue consumers and delivers through the exporter. It
 * listens to the path finder, so a queue is delivered from the moment it is created until it
 * is removed.</p>
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
 * optional handlers (backup, metrics, ...)
 *
 * PathFinder creates a queue -> Dispatcher.start(queue) -> DispatchExporter
 * }</pre>
 *
 * @author yun
 */
abstract class AbstractExecutorGateway implements Gateway {

    private static final Logger log = LoggerFactory.getLogger(AbstractExecutorGateway.class);
    private static final long SHUTDOWN_TIMEOUT_SECONDS = 60;

    private final ExecutorService executor;
    private final PathFinder pathFinder;
    private final Dispatcher dispatcher;
    private final AtomicBoolean closed = new AtomicBoolean();
    private DispatchHandlerChain handlerChain;

    protected AbstractExecutorGateway(
            ExecutorService executor,
            DispatchExporter exporter,
            PathFinder pathFinder
    ) {
        this(
                executor,
                exporter,
                pathFinder,
                FanoutDispatcher.DEFAULT_MAX_MESSAGE_BLOCK_SIZE,
                FanoutDispatcher.DEFAULT_MAX_MESSAGE_BLOCK_BYTES
        );
    }

    protected AbstractExecutorGateway(
            ExecutorService executor,
            DispatchExporter exporter,
            PathFinder pathFinder,
            int maxMessageBlockSize,
            int maxMessageBlockBytes
    ) {
        this.executor = executor;
        this.pathFinder = pathFinder;
        FanoutDispatcher fanoutDispatcher =
                new FanoutDispatcher(executor, exporter, maxMessageBlockSize, maxMessageBlockBytes);
        this.dispatcher = fanoutDispatcher;
        pathFinder.listen(fanoutDispatcher);
        this.handlerChain = DispatchHandlerChain.chain(executor)
                .add(new RouteDispatchChainHandler(pathFinder));
    }

    @Override
    public Gateway chainHandler(Handler<DispatchHandlerChain> chainHandler) {
        DispatchHandlerChain chain = DispatchHandlerChain.chain(executor);
        chain.add(new RouteDispatchChainHandler(pathFinder));
        chainHandler.handle(chain);
        this.handlerChain = chain;
        return this;
    }

    @Override
    public CompletableFuture<@Nullable Void> sparkDispatch(Message message) {
        Assert.checkNotNull(message, "message");

        if (closed.get()) {
            throw new IllegalStateException("Gateway is closed");
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

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            dispatcher.close();
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
