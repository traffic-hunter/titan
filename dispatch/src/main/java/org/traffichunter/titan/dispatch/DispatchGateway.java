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
import org.traffichunter.titan.dispatch.exporter.DispatchExporter;

/**
 * Starts asynchronous message dispatch for fanout delivery.
 *
 * <p>Each dispatch starts the configured handler chain. Its handlers perform routing and fanout.
 * The returned future completes when the chain finishes, not when acknowledgements arrive from
 * every subscribed client.</p>
 *
 * @author yungwang-o
 */
public interface DispatchGateway extends Closeable, DispatcherQueueManager {

    static DispatchGateway of(DispatchExporter exporter) {
        return new VirtualThreadExecutorDispatchGateway(exporter);
    }

    static DispatchGateway of(DispatchExporter exporter, Dispatcher dispatcher) {
        return new VirtualThreadExecutorDispatchGateway(exporter, dispatcher);
    }

    /**
     * Configures the dispatch handler chain used by {@link #sparkDispatch(Message)}.
     *
     * <p>The gateway installs routing before the callback and fanout after the
     * callback. Custom handlers therefore run after the message is routed into
     * the dispatcher queue and before the destination consumer is started. This
     * is the extension point for backup, metrics, validation, and filtering.
     * Admission controls that must run before routing can be inserted with
     * {@link DispatchHandlerChain#addFirst(DispatchChainHandler)}.</p>
     *
     * @param chainHandler callback that adds custom handlers to the chain
     * @return this gateway
     */
    @CanIgnoreReturnValue
    DispatchGateway chainHandler(Handler<DispatchHandlerChain> chainHandler);

    /**
     * Sparks one message through the configured routing and fanout handler chain.
     *
     * <p>The returned future completes with {@code null}; the value is only a
     * completion signal.</p>
     */
    CompletableFuture<@Nullable Void> sparkDispatch(Message message);

    boolean isOpen();

    boolean isClosed();
}
