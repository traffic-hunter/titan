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

import org.traffichunter.titan.core.channel.stomp.StompClientChannel;
import org.traffichunter.titan.core.codec.stomp.StompException;
import org.traffichunter.titan.core.codec.stomp.StompFrames;
import org.traffichunter.titan.core.codec.stomp.StompHeaders;
import org.traffichunter.titan.core.codec.stomp.StompHeaders.Elements;
import org.traffichunter.titan.core.util.Destination;
import org.traffichunter.titan.core.util.Handler;
import org.traffichunter.titan.core.util.buffer.Buffer;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Transport-neutral adapter for a Titan native STOMP client channel.
 *
 * <p>The adapter maps Titan {@code Promise} results and native STOMP frames to the
 * {@link CompletableFuture}-based {@link StompConnection} contract. Handler callbacks are bound
 * to the current native channel, and identity checks prevent late callbacks from a superseded
 * channel from being forwarded.</p>
 *
 * @author yun
 */
final class TitanStompConnection implements StompConnection {

    private volatile StompClientChannel connection;
    private final Handler<StompConnection> connectionLostHandler;
    private volatile Handler<StompFrames> errorHandler = frame -> {};
    private volatile Handler<StompConnection> closeHandler = operations -> {};
    private volatile Handler<StompConnection> connectionDroppedHandler = operations -> {};
    private volatile Handler<StompConnection> pingHandler = operations -> {};
    private volatile Handler<Throwable> exceptionHandler = error -> {};

    public TitanStompConnection(StompClientChannel connection) {
        this(connection, operations -> {});
    }

    public TitanStompConnection(
            StompClientChannel connection,
            Handler<StompConnection> connectionLostHandler
    ) {
        this.connection = connection;
        this.connectionLostHandler = connectionLostHandler;
        installHandlers(connection);
    }

    @Override
    public CompletableFuture<StompFrames> send(String destination, Buffer payload) {
        try {
            validateDestination(destination);
        } catch (RuntimeException error) {
            payload.release();
            throw error;
        }
        return connection.send(destination, payload)
                .map(StompFrames::from)
                .toCompletableFuture();
    }

    @Override
    public CompletableFuture<StompFrames> send(String destination, Buffer payload, Map<Elements, String> headers) {
        StompHeaders stompHeaders;
        try {
            validateDestination(destination);
            stompHeaders = toHeaders(headers);
        } catch (RuntimeException error) {
            payload.release();
            throw error;
        }
        return connection.send(destination, payload, stompHeaders)
                .map(StompFrames::from)
                .toCompletableFuture();
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
        StompHeaders stompHeaders = toHeaders(headers);
        String subscriptionId = stompHeaders.getOrDefault(Elements.ID, destination);
        return connection.subscribe(destination, stompHeaders, handler::handle)
                .map(frame -> subscriptionId)
                .toCompletableFuture();
    }

    @Override
    public CompletableFuture<StompFrames> unsubscribe(String subscriptionId) {
        return unsubscribe(subscriptionId, Map.of());
    }

    @Override
    public CompletableFuture<StompFrames> unsubscribe(String subscriptionId, Map<Elements, String> headers) {
        StompHeaders stompHeaders = toHeaders(headers);
        stompHeaders.put(Elements.ID, subscriptionId);
        return connection.unsubscribe(subscriptionId, stompHeaders)
                .map(StompFrames::from)
                .toCompletableFuture();
    }

    @Override
    public CompletableFuture<StompFrames> ack(String messageId) {
        return connection.ack(messageId)
                .map(StompFrames::from)
                .toCompletableFuture();
    }

    @Override
    public CompletableFuture<StompFrames> nack(String messageId) {
        return connection.nack(messageId)
                .map(StompFrames::from)
                .toCompletableFuture();
    }

    @Override
    public CompletableFuture<StompFrames> disconnect() {
        return connection.disconnect()
                .map(StompFrames::from)
                .toCompletableFuture();
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

    /** Rebinds this facade to a newly negotiated native channel after reconnect. */
    void replace(StompClientChannel connection) {
        this.connection = connection;
        installHandlers(connection);
    }

    private static StompHeaders toHeaders(Map<Elements, String> headers) {
        StompHeaders stompHeaders = StompHeaders.create();
        headers.forEach(stompHeaders::put);
        return stompHeaders;
    }

    private static void validateDestination(String destination) {
        Destination.create(destination);
    }

    private void installHandlers(StompClientChannel connection) {
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
        connection.handler().errorHandler((event, context) -> {
            errorHandler.handle(event.frame());
            event.connection().failConnect(new StompException("Received ERROR frame from server"));
            event.connection().error(event.frame());
        });
        connection.handler().pingHandler((event, context) -> pingHandler.handle(this));
    }
}
