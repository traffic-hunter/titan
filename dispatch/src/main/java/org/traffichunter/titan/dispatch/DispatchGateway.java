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

import java.io.Closeable;
import java.util.concurrent.CompletableFuture;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.message.Message;
import org.traffichunter.titan.core.util.Handler;
import org.traffichunter.titan.dispatch.exporter.DispatchExporter;

/**
 * Asynchronous ingress and routing facade for fanout delivery.
 *
 * <p>The gateway is the public entry point for one message dispatch lifecycle. Dispatching starts
 * the configured handler chain; routing and fanout remain internal handler responsibilities. The
 * returned future represents completion of that chain, not remote protocol acknowledgement from
 * every subscribed client.</p>
 *
 * @author yungwang-o
 */
public interface DispatchGateway extends Closeable, DispatcherQueueManager {

    static DispatchGateway ofThread(DispatchExporter exporter) {
        return new ThreadPoolExecutorDispatchGateway(exporter);
    }

    static DispatchGateway ofThread(DispatchExporter exporter, Dispatcher dispatcher) {
        return new ThreadPoolExecutorDispatchGateway(exporter, dispatcher);
    }

    static DispatchGateway ofVirtual(DispatchExporter exporter) {
        return new VirtualThreadExecutorDispatchGateway(exporter);
    }

    static DispatchGateway ofVirtual(DispatchExporter exporter, Dispatcher dispatcher) {
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
