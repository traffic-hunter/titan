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
     * <p>An implementation assigns an identifier of its own whenever the headers name none, so
     * two subscriptions never share one. The same destination in two groups is two
     * subscriptions, and a destination may also be subscribed to twice within one group.</p>
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
