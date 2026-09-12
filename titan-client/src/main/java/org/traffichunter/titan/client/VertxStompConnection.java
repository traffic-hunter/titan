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

import io.vertx.ext.stomp.StompClientConnection;
import org.traffichunter.titan.core.codec.stomp.StompFrames;
import org.traffichunter.titan.core.codec.stomp.StompHeaders.Elements;
import org.traffichunter.titan.core.codec.stomp.vertx.VertxStompFrame;
import org.traffichunter.titan.core.util.Destination;
import org.traffichunter.titan.core.util.Handler;
import org.traffichunter.titan.core.util.IdGenerator;
import org.traffichunter.titan.core.util.buffer.Buffer;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Transport-neutral adapter for a Vert.x STOMP client connection.
 *
 * <p>The adapter converts Titan buffers and headers to Vert.x values, wraps received frames in the
 * common {@link StompFrames} contract, and bridges Vert.x futures to {@link CompletableFuture}.
 * Identity checks prevent callbacks from a superseded native connection from reaching the
 * logical client.</p>
 *
 * @author yun
 */
final class VertxStompConnection implements StompConnection {

    private volatile StompClientConnection connection;
    private final Handler<StompConnection> connectionLostHandler;
    private volatile Handler<StompFrames> errorHandler = frame -> {};
    private volatile Handler<StompConnection> closeHandler = operations -> {};
    private volatile Handler<StompConnection> connectionDroppedHandler = operations -> {};
    private volatile Handler<StompConnection> pingHandler = operations -> {};
    private volatile Handler<Throwable> exceptionHandler = error -> {};

    public VertxStompConnection(StompClientConnection connection) {
        this(connection, operations -> {});
    }

    public VertxStompConnection(
            StompClientConnection connection,
            Handler<StompConnection> connectionLostHandler
    ) {
        this.connection = connection;
        this.connectionLostHandler = connectionLostHandler;
        installHandlers(connection);
    }

    /** Rebinds this facade to a newly negotiated Vert.x connection after reconnect. */
    void replace(StompClientConnection connection) {
        this.connection = connection;
        installHandlers(connection);
    }

    private void installHandlers(StompClientConnection connection) {
        connection.closeHandler(ignored -> {
            if (this.connection != connection) {
                return;
            }
            connectionLostHandler.handle(this);
            closeHandler.handle(this);
        });
        connection.connectionDroppedHandler(ignored -> {
            if (this.connection != connection) {
                return;
            }
            connectionLostHandler.handle(this);
            connectionDroppedHandler.handle(this);
        });
        connection.exceptionHandler(error -> {
            if (this.connection != connection) {
                return;
            }
            exceptionHandler.handle(error);
        });
        connection.errorHandler(frame -> errorHandler.handle(VertxStompFrame.wrap(frame)));
        connection.pingHandler(ignored -> pingHandler.handle(this));
    }

    @Override
    public CompletableFuture<StompFrames> send(String destination, Buffer payload) {
        try {
            validateDestination(destination);
            return toFuture(connection.send(destination, toVertxBuffer(payload)));
        } finally {
            payload.release();
        }
    }

    @Override
    public CompletableFuture<StompFrames> send(String destination, Buffer payload, Map<Elements, String> headers) {
        try {
            validateDestination(destination);
            return toFuture(connection.send(destination, toVertxHeaders(headers), toVertxBuffer(payload)));
        } finally {
            payload.release();
        }
    }

    @Override
    public CompletableFuture<String> subscribe(String destination, Handler<StompFrames> handler) {
        return subscribe(destination, Map.of(), handler);
    }

    @Override
    public CompletableFuture<String> subscribe(
            String destination,
            Map<Elements, String> headers,
            Handler<StompFrames> handler
    ) {
        validateDestination(destination);
        Map<String, String> vertxHeaders = toVertxHeaders(headers);
        // Vert.x falls back to the destination as the identifier and refuses a second
        // subscription that ends up with one already taken, which is what two groups holding the
        // same destination would do. It returns whichever identifier the headers name.
        vertxHeaders.putIfAbsent(Elements.ID.getName(), IdGenerator.uuid());
        return connection.subscribe(
                destination,
                vertxHeaders,
                frame -> handler.handle(VertxStompFrame.wrap(frame))
        ).toCompletionStage().toCompletableFuture();
    }

    @Override
    public CompletableFuture<StompFrames> unsubscribe(String subscriptionId) {
        return toFuture(connection.unsubscribe(subscriptionId));
    }

    @Override
    public CompletableFuture<StompFrames> unsubscribe(String subscriptionId, Map<Elements, String> headers) {
        return toFuture(connection.unsubscribe(subscriptionId, toVertxHeaders(headers)));
    }

    @Override
    public CompletableFuture<StompFrames> ack(String messageId) {
        return toFuture(connection.ack(messageId));
    }

    @Override
    public CompletableFuture<StompFrames> nack(String messageId) {
        return toFuture(connection.nack(messageId));
    }

    @Override
    public CompletableFuture<StompFrames> disconnect() {
        return toFuture(connection.disconnect());
    }

    @Override
    public StompConnection errorHandler(Handler<StompFrames> handler) {
        this.errorHandler = handler;
        return this;
    }

    @Override
    public StompConnection closeHandler(Handler<StompConnection> handler) {
        this.closeHandler = handler;
        return this;
    }

    @Override
    public StompConnection connectionDroppedHandler(Handler<StompConnection> handler) {
        this.connectionDroppedHandler = handler;
        return this;
    }

    @Override
    public StompConnection pingHandler(Handler<StompConnection> handler) {
        this.pingHandler = handler;
        return this;
    }

    @Override
    public StompConnection exceptionHandler(Handler<Throwable> handler) {
        this.exceptionHandler = handler;
        return this;
    }

    @Override
    public boolean isConnected() {
        return connection.isConnected();
    }

    private static CompletableFuture<StompFrames> toFuture(
            io.vertx.core.Future<io.vertx.ext.stomp.Frame> future
    ) {
        return future.map(frame -> (StompFrames) VertxStompFrame.wrap(frame))
                .toCompletionStage()
                .toCompletableFuture();
    }

    private static io.vertx.core.buffer.Buffer toVertxBuffer(Buffer buffer) {
        return io.vertx.core.buffer.Buffer.buffer(buffer.getBytes());
    }

    private static Map<String, String> toVertxHeaders(Map<Elements, String> headers) {
        Map<String, String> converted = new HashMap<>();
        headers.forEach((element, value) -> converted.put(element.getName(), value));
        return converted;
    }

    private static void validateDestination(String destination) {
        Destination.create(destination);
    }
}
