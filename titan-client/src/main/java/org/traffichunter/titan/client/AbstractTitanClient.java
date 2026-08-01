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

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.traffichunter.titan.core.codec.stomp.StompFrames;
import org.traffichunter.titan.core.codec.stomp.StompHeaders.Elements;
import org.traffichunter.titan.core.util.Handler;
import org.traffichunter.titan.core.util.buffer.Buffer;

/**
 * Shares transport-neutral operation delegation between internal client implementations.
 *
 * <p>Public operations are forwarded to a stable {@link StompConnection} facade. Concrete
 * clients remain responsible for lifecycle, transport creation, reconnect coordination, and
 * replacing the native connection held by that facade.</p>
 *
 * @author yun
 */
abstract class AbstractTitanClient implements TitanClient {

    @Override
    public CompletableFuture<TitanClient> connect() {
        return connectConnection().thenApply(ignored -> this);
    }

    @Override
    public CompletableFuture<StompFrames> send(String destination, Buffer payload) {
        return connection().send(destination, payload);
    }

    @Override
    public CompletableFuture<StompFrames> send(
            String destination,
            Buffer payload,
            Map<Elements, String> headers
    ) {
        return connection().send(destination, payload, headers);
    }

    @Override
    public CompletableFuture<String> subscribe(String destination, Handler<StompFrames> handler) {
        return connection().subscribe(destination, handler);
    }

    @Override
    public CompletableFuture<String> subscribe(
            String destination,
            Map<Elements, String> headers,
            Handler<StompFrames> handler
    ) {
        return connection().subscribe(destination, headers, handler);
    }

    @Override
    public CompletableFuture<StompFrames> unsubscribe(String subscriptionId) {
        return connection().unsubscribe(subscriptionId);
    }

    @Override
    public CompletableFuture<StompFrames> unsubscribe(String subscriptionId, Map<Elements, String> headers) {
        return connection().unsubscribe(subscriptionId, headers);
    }

    @Override
    public CompletableFuture<StompFrames> ack(String messageId) {
        return connection().ack(messageId);
    }

    @Override
    public CompletableFuture<StompFrames> nack(String messageId) {
        return connection().nack(messageId);
    }

    @Override
    public CompletableFuture<StompFrames> disconnect() {
        return connection().disconnect();
    }

    @Override
    public TitanClient errorHandler(Handler<StompFrames> handler) {
        connection().errorHandler(handler);
        return this;
    }

    @Override
    public TitanClient closeHandler(Handler<TitanClient> handler) {
        connection().closeHandler(ignored -> handler.handle(this));
        return this;
    }

    @Override
    public TitanClient connectionDroppedHandler(Handler<TitanClient> handler) {
        connection().connectionDroppedHandler(ignored -> handler.handle(this));
        return this;
    }

    @Override
    public TitanClient pingHandler(Handler<TitanClient> handler) {
        connection().pingHandler(ignored -> handler.handle(this));
        return this;
    }

    @Override
    public TitanClient exceptionHandler(Handler<Throwable> handler) {
        connection().exceptionHandler(handler);
        return this;
    }

    @Override
    public boolean isConnected() {
        return connection().isConnected();
    }

    /** Starts implementation-specific transport and STOMP negotiation. */
    abstract CompletableFuture<StompConnection> connectConnection();

    /** Returns the stable connection facade or fails when no connection has been established. */
    abstract StompConnection connection();
}
