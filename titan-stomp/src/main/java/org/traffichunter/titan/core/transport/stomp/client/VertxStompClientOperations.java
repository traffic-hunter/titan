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
package org.traffichunter.titan.core.transport.stomp.client;

import io.vertx.ext.stomp.StompClientConnection;
import org.jspecify.annotations.NullMarked;
import org.traffichunter.titan.core.codec.stomp.StompFrames;
import org.traffichunter.titan.core.codec.stomp.StompHeaders.Elements;
import org.traffichunter.titan.core.codec.stomp.vertx.VertxStompFrame;
import org.traffichunter.titan.core.util.Destination;
import org.traffichunter.titan.core.util.Handler;
import org.traffichunter.titan.core.util.buffer.Buffer;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @author yun
 */
public final class VertxStompClientOperations implements StompClientOperations {

    private final StompClientConnection connection;

    public VertxStompClientOperations(StompClientConnection connection) {
        this.connection = connection;
    }

    @Override
    public Future<StompFrames> send(String destination, Buffer payload) {
        validateDestination(destination);
        return toFuture(connection.send(destination, toVertxBuffer(payload)));
    }

    @Override
    public Future<StompFrames> send(String destination, Buffer payload, Map<Elements, String> headers) {
        validateDestination(destination);
        return toFuture(connection.send(destination, toVertxHeaders(headers), toVertxBuffer(payload)));
    }

    @Override
    public Future<String> subscribe(String destination, Handler<StompFrames> handler) {
        validateDestination(destination);
        return VertxFutureWrapper.wrap(connection.subscribe(
                destination,
                frame -> handler.handle(VertxStompFrame.wrap(frame))
        ));
    }

    @Override
    public Future<String> subscribe(String destination, Map<Elements, String> headers, Handler<StompFrames> handler) {
        validateDestination(destination);
        return VertxFutureWrapper.wrap(connection.subscribe(
                destination,
                toVertxHeaders(headers),
                frame -> handler.handle(VertxStompFrame.wrap(frame))
        ));
    }

    @Override
    public Future<StompFrames> unsubscribe(String subscriptionId) {
        return toFuture(connection.unsubscribe(subscriptionId));
    }

    @Override
    public Future<StompFrames> unsubscribe(String subscriptionId, Map<Elements, String> headers) {
        return toFuture(connection.unsubscribe(subscriptionId, toVertxHeaders(headers)));
    }

    @Override
    public Future<StompFrames> ack(String messageId) {
        return toFuture(connection.ack(messageId));
    }

    @Override
    public Future<StompFrames> nack(String messageId) {
        return toFuture(connection.nack(messageId));
    }

    @Override
    public Future<StompFrames> disconnect() {
        return toFuture(connection.disconnect());
    }

    @Override
    public StompClientOperations errorHandler(Handler<StompFrames> handler) {
        connection.errorHandler(frame -> handler.handle(VertxStompFrame.wrap(frame)));
        return this;
    }

    @Override
    public StompClientOperations closeHandler(Handler<StompClientOperations> handler) {
        connection.closeHandler(connection -> handler.handle(this));
        return this;
    }

    @Override
    public StompClientOperations connectionDroppedHandler(Handler<StompClientOperations> handler) {
        connection.connectionDroppedHandler(connection -> handler.handle(this));
        return this;
    }

    @Override
    public StompClientOperations pingHandler(Handler<StompClientOperations> handler) {
        connection.pingHandler(connection -> handler.handle(this));
        return this;
    }

    @Override
    public StompClientOperations exceptionHandler(Handler<Throwable> handler) {
        connection.exceptionHandler(handler::handle);
        return this;
    }

    @Override
    public boolean isConnected() {
        return connection.isConnected();
    }

    @NullMarked
    private record VertxFutureWrapper<V>(CompletableFuture<V> future) implements Future<V> {

        private VertxFutureWrapper(io.vertx.core.Future<V> future) {
            this(future.toCompletionStage().toCompletableFuture());
        }

        static <V> Future<V> wrap(io.vertx.core.Future<V> future) {
            return new VertxFutureWrapper<>(future);
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return future.cancel(mayInterruptIfRunning);
        }

        @Override
        public boolean isCancelled() {
            return future.isCancelled();
        }

        @Override
        public boolean isDone() {
            return future.isDone();
        }

        @Override
        public V get() throws InterruptedException, ExecutionException {
            return future.get();
        }

        @Override
        public V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            return future.get(timeout, unit);
        }
    }

    private static Future<StompFrames> toFuture(io.vertx.core.Future<io.vertx.ext.stomp.Frame> future) {
        return VertxFutureWrapper.wrap(future.map(VertxStompFrame::wrap));
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
