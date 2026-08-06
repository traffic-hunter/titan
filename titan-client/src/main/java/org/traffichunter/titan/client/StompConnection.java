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
 * Transport-neutral view of one active physical STOMP connection.
 *
 * <p>Titan-native and Vert.x adapters implement this contract so {@link DefaultTitanClient} can
 * delegate messaging operations without exposing transport-specific frame or connection types.
 * A reconnect creates a new instance, after which the facade installs its current handlers on
 * that instance before publishing it as the active connection.</p>
 *
 * @author yun
 */
public interface StompConnection {

    /**
     * Sends a message using the connection's default headers.
     *
     * <p>The connection consumes exactly one reference from {@code payload}, including validation
     * and transport failure paths. Callers must not release or reuse it after invocation.</p>
     *
     * @param destination target STOMP destination
     * @param payload message payload whose ownership is transferred to this connection
     * @return future completed with the resulting transport frame
     */
    CompletableFuture<StompFrames> send(String destination, Buffer payload);

    /**
     * Sends a message with explicit STOMP headers.
     *
     * <p>This overload follows the same ownership-transfer contract as
     * {@link #send(String, Buffer)}.</p>
     *
     * @param destination target STOMP destination
     * @param payload message payload whose ownership is transferred to this connection
     * @param headers additional STOMP headers
     * @return future completed with the resulting transport frame
     */
    CompletableFuture<StompFrames> send(String destination, Buffer payload, Map<Elements, String> headers);

    /**
     * Creates a subscription using connection defaults.
     *
     * @param destination destination to subscribe to
     * @param handler handler for received MESSAGE frames
     * @return future completed with the assigned subscription identifier
     */
    CompletableFuture<String> subscribe(String destination, Handler<StompFrames> handler);

    /**
     * Creates a subscription using explicit STOMP headers.
     *
     * @param destination destination to subscribe to
     * @param headers additional SUBSCRIBE headers
     * @param handler handler for received MESSAGE frames
     * @return future completed with the assigned subscription identifier
     */
    CompletableFuture<String> subscribe(String destination, Map<Elements, String> headers, Handler<StompFrames> handler);

    /**
     * Removes the identified subscription.
     *
     * @param subscriptionId identifier returned by subscribe
     * @return future completed with the resulting transport frame
     */
    CompletableFuture<StompFrames> unsubscribe(String subscriptionId);

    /**
     * Removes the identified subscription with additional headers.
     *
     * @param subscriptionId identifier returned by subscribe
     * @param headers additional UNSUBSCRIBE headers
     * @return future completed with the resulting transport frame
     */
    CompletableFuture<StompFrames> unsubscribe(String subscriptionId, Map<Elements, String> headers);

    /**
     * Sends a positive acknowledgement for a received message.
     *
     * @param messageId identifier of the received message
     * @return future completed with the resulting transport frame
     */
    CompletableFuture<StompFrames> ack(String messageId);

    /**
     * Sends a negative acknowledgement for a received message.
     *
     * @param messageId identifier of the received message
     * @return future completed with the resulting transport frame
     */
    CompletableFuture<StompFrames> nack(String messageId);

    /**
     * Gracefully ends the STOMP session.
     *
     * @return future completed after the disconnect operation is issued
     */
    CompletableFuture<StompFrames> disconnect();

    /**
     * Registers the STOMP ERROR frame handler.
     *
     * @param handler handler to invoke for ERROR frames
     * @return this connection
     */
    @CanIgnoreReturnValue
    StompConnection errorHandler(Handler<StompFrames> handler);

    /**
     * Registers the normal connection-close handler.
     *
     * @param handler handler to invoke when the connection closes
     * @return this connection
     */
    @CanIgnoreReturnValue
    StompConnection closeHandler(Handler<StompConnection> handler);

    /**
     * Registers the unexpected connection-loss handler.
     *
     * @param handler handler to invoke when the transport connection is lost
     * @return this connection
     */
    @CanIgnoreReturnValue
    StompConnection connectionDroppedHandler(Handler<StompConnection> handler);

    /**
     * Registers the incoming heartbeat handler.
     *
     * @param handler handler to invoke for an incoming STOMP heartbeat
     * @return this connection
     */
    @CanIgnoreReturnValue
    StompConnection pingHandler(Handler<StompConnection> handler);

    /**
     * Registers the asynchronous protocol and transport failure handler.
     *
     * @param handler handler to invoke for asynchronous failures
     * @return this connection
     */
    @CanIgnoreReturnValue
    StompConnection exceptionHandler(Handler<Throwable> handler);

    /**
     * Returns whether the underlying transport connection is active.
     *
     * @return {@code true} while the physical connection is active
     */
    boolean isConnected();
}
