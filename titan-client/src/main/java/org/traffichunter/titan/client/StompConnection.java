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
package org.traffichunter.titan.client;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import org.traffichunter.titan.core.codec.stomp.StompFrames;
import org.traffichunter.titan.core.util.Handler;
import org.traffichunter.titan.core.util.buffer.Buffer;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.traffichunter.titan.core.codec.stomp.StompHeaders.*;

/**
 * Transport-neutral active STOMP client connection.
 *
 * <p>This contract is internal to the facade. Native and Vert.x connections implement it so
 * {@link AbstractTitanClient} can expose one stable public API and preserve handler registration
 * while an implementation replaces its underlying connection during reconnect.</p>
 *
 * @author yun
 */
interface StompConnection {

    /** Sends a message using the connection's default headers. */
    CompletableFuture<StompFrames> send(String destination, Buffer payload);

    /** Sends a message with explicit STOMP headers. */
    CompletableFuture<StompFrames> send(String destination, Buffer payload, Map<Elements, String> headers);

    /** Creates a subscription using connection defaults. */
    CompletableFuture<String> subscribe(String destination, Handler<StompFrames> handler);

    /** Creates a subscription using explicit STOMP headers. */
    CompletableFuture<String> subscribe(String destination, Map<Elements, String> headers, Handler<StompFrames> handler);

    /** Removes the identified subscription. */
    CompletableFuture<StompFrames> unsubscribe(String subscriptionId);

    /** Removes the identified subscription with additional headers. */
    CompletableFuture<StompFrames> unsubscribe(String subscriptionId, Map<Elements, String> headers);

    /** Sends a positive acknowledgement for a received message. */
    CompletableFuture<StompFrames> ack(String messageId);

    /** Sends a negative acknowledgement for a received message. */
    CompletableFuture<StompFrames> nack(String messageId);

    /** Gracefully ends the STOMP session. */
    CompletableFuture<StompFrames> disconnect();

    /** Registers the STOMP ERROR frame handler. */
    @CanIgnoreReturnValue
    StompConnection errorHandler(Handler<StompFrames> handler);

    /** Registers the normal connection-close handler. */
    @CanIgnoreReturnValue
    StompConnection closeHandler(Handler<StompConnection> handler);

    /** Registers the unexpected connection-loss handler. */
    @CanIgnoreReturnValue
    StompConnection connectionDroppedHandler(Handler<StompConnection> handler);

    /** Registers the incoming heartbeat handler. */
    @CanIgnoreReturnValue
    StompConnection pingHandler(Handler<StompConnection> handler);

    /** Registers the asynchronous protocol and transport failure handler. */
    @CanIgnoreReturnValue
    StompConnection exceptionHandler(Handler<Throwable> handler);

    /** Returns whether the underlying transport connection is active. */
    boolean isConnected();
}
