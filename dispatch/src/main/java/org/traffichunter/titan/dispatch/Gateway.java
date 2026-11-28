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

import java.io.Closeable;
import java.util.concurrent.CompletableFuture;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.message.Message;
import org.traffichunter.titan.core.util.Handler;

/**
 * Puts messages into their destination queues.
 *
 * <p>{@link #sparkDispatch(Message)} runs the handler chain on the calling thread, and the chain
 * puts the message into its destination queue. A {@link Dispatcher} delivers from the queue
 * later. The returned future completes when the chain has finished. It does not wait for
 * subscribers to receive or acknowledge the message.</p>
 *
 * <p>This type is not part of Titan's stable API and can change in any release. The supported
 * way to run a server is {@code TitanBootstrap.run(String)} with {@code titan-env.yml}.</p>
 *
 * @author yungwang-o
 */
public interface Gateway extends Closeable {

    /** Returns a gateway that routes messages into the queues of the path finder. */
    static Gateway of(PathFinder pathFinder) {
        return new DefaultGateway(pathFinder);
    }

    /**
     * Configures the dispatch handler chain used by {@link #sparkDispatch(Message)}.
     *
     * <p>The gateway adds routing before it calls the callback, so handlers added here run
     * after the message is in its queue. Use them for backup, metrics, validation or
     * filtering. A handler that has to run before routing, such as admission control, goes in
     * with {@link DispatchHandlerChain#addFirst(DispatchChainHandler)}.</p>
     *
     * @param chainHandler callback that adds custom handlers to the chain
     * @return this gateway
     */
    @CanIgnoreReturnValue
    Gateway chainHandler(Handler<DispatchHandlerChain> chainHandler);

    /**
     * Runs the handler chain for one message.
     *
     * <p>The returned future completes with {@code null}. Only its completion means
     * anything.</p>
     */
    CompletableFuture<@Nullable Void> sparkDispatch(Message message);

    boolean isOpen();

    boolean isClosed();

    /** Stops admitting messages. */
    @Override
    void close();
}
