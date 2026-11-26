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
import org.traffichunter.titan.core.message.Message;
import org.traffichunter.titan.core.util.Assert;
import org.traffichunter.titan.core.util.Handler;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * {@link Gateway} that runs its handler chain on the thread that calls
 * {@link #sparkDispatch(Message)}.
 *
 * <p>The message is in its queue when {@code sparkDispatch} returns, so messages one caller
 * sends are enqueued in the order it sends them. The handler chain ends at the queue:
 * {@link RouteDispatchChainHandler} admits messages to destination queues, and custom handlers
 * run after it. A handler that blocks holds up its caller, which is the server's event loop.</p>
 *
 * <p>Delivery from the queues is not the gateway's job. A {@link Dispatcher} built on the same
 * path finder takes it from there.</p>
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
 * }</pre>
 *
 * @author yun
 */
final class DefaultGateway implements Gateway {

    private final PathFinder pathFinder;
    private final AtomicBoolean closed = new AtomicBoolean();
    private DispatchHandlerChain handlerChain;

    DefaultGateway(PathFinder pathFinder) {
        this.pathFinder = pathFinder;
        this.handlerChain = DispatchHandlerChain.chain()
                .add(new RouteDispatchChainHandler(pathFinder));
    }

    @Override
    public Gateway chainHandler(Handler<DispatchHandlerChain> chainHandler) {
        DispatchHandlerChain chain = DispatchHandlerChain.chain();
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
            handlerChain.clear();
        }
    }
}
