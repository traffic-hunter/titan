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

import java.io.Closeable;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.message.Message;
import org.traffichunter.titan.core.message.dispatcher.Dispatcher;
import org.traffichunter.titan.core.message.dispatcher.DispatcherQueueManager;
import org.traffichunter.titan.core.util.Destination;
import org.traffichunter.titan.core.util.Handler;
import org.traffichunter.titan.fanout.exporter.FanoutExporter;

/**
 * Asynchronous ingress and routing facade for fanout delivery.
 *
 * <p>The gateway has two responsibilities:</p>
 *
 * <ul>
 *     <li>{@link #publish(Message)} enqueues producer messages into the
 *     dispatcher queue keyed by {@link Destination}.</li>
 *     <li>{@link #fanout(Destination)} starts one long-lived consumer task for
 *     a destination, if it has not already been started.</li>
 * </ul>
 *
 * <p>Callers normally publish first and let the implementation ensure that the
 * matching destination consumer exists. The returned futures represent gateway
 * task submission, not necessarily remote protocol acknowledgement for every
 * subscribed client.</p>
 *
 * @author yungwang-o
 */
public interface FanoutGateway extends Closeable, DispatcherQueueManager {

    static FanoutGateway ofThread(FanoutExporter exporter) {
        return new ThreadPoolExecutorFanoutGateway(exporter);
    }

    static FanoutGateway ofVirtual(FanoutExporter exporter) {
        return new VirtualThreadExecutorFanoutGateway(exporter);
    }

    /**
     * Configures the fanout handler chain used by {@link #publish(Message)}.
     *
     * <p>The gateway adds the built-in route handler before invoking the callback and
     * appends the built-in dispatch handler after the callback returns. Handlers added
     * by the callback therefore run between routing and dispatch, which is the intended
     * extension point for cross-cutting fanout behavior such as backup, metrics, or
     * filtering.</p>
     *
     * @param chainHandler callback that adds custom handlers to the chain
     * @return this gateway
     */
    @CanIgnoreReturnValue
    FanoutGateway chainHandler(Handler<FanoutHandlerChain> chainHandler);

    /**
     * Starts consumers for the destinations if they are not already running.
     *
     * <p>The returned futures complete with {@code null}; the value is only a
     * completion signal.</p>
     */
    List<CompletableFuture<@Nullable Void>> fanout(Collection<Destination> destinations);

    /**
     * Starts the consumer for a destination if it is not already running.
     *
     * <p>The returned future completes with {@code null}; the value is only a
     * completion signal.</p>
     */
    CompletableFuture<@Nullable Void> fanout(Destination destination);

    /**
     * Publishes messages into destination queues.
     *
     * <p>The returned futures represent enqueue and consumer-start submission,
     * not delivery acknowledgement from subscribed clients.</p>
     */
    List<CompletableFuture<@Nullable Void>> publish(Collection<Message> messages);

    /**
     * Publishes one message into its destination queue.
     *
     * <p>The returned future completes with {@code null}; the value is only a
     * completion signal.</p>
     */
    CompletableFuture<@Nullable Void> publish(Message message);

    boolean isOpen();

    boolean isClosed();
}
