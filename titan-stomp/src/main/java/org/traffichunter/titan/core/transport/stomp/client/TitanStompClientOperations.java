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

import org.traffichunter.titan.core.channel.stomp.StompClientConnection;
import org.traffichunter.titan.core.codec.stomp.StompFrames;
import org.traffichunter.titan.core.codec.stomp.StompHeaders;
import org.traffichunter.titan.core.codec.stomp.StompHeaders.Elements;
import org.traffichunter.titan.core.util.Handler;
import org.traffichunter.titan.core.util.buffer.Buffer;

import java.util.Map;
import java.util.concurrent.Future;

/**
 * @author yun
 */
public final class TitanStompClientOperations implements StompClientOperations {

    private final StompClientConnection connection;

    public TitanStompClientOperations(StompClientConnection connection) {
        this.connection = connection;
    }

    @Override
    public Future<StompFrames> send(String destination, Buffer payload) {
        return connection.send(destination, payload)
                .map(f -> (StompFrames) f)
                .future();
    }

    @Override
    public Future<StompFrames> send(String destination, Buffer payload, Map<Elements, String> headers) {
        return connection.send(destination, payload, toHeaders(headers))
                .map(f -> (StompFrames) f)
                .future();
    }

    @Override
    public Future<String> subscribe(String destination, Handler<StompFrames> handler) {
        return subscribe(destination, Map.of(), handler);
    }

    @Override
    public Future<String> subscribe(String destination, Map<Elements, String> headers, Handler<StompFrames> handler) {
        StompHeaders stompHeaders = toHeaders(headers);
        String subscriptionId = stompHeaders.getOrDefault(Elements.ID, destination);
        return connection.subscribe(destination, stompHeaders, handler::handle)
                .map(frame -> subscriptionId)
                .future();
    }

    @Override
    public Future<StompFrames> unsubscribe(String subscriptionId) {
        return unsubscribe(subscriptionId, Map.of());
    }

    @Override
    public Future<StompFrames> unsubscribe(String subscriptionId, Map<Elements, String> headers) {
        StompHeaders stompHeaders = toHeaders(headers);
        stompHeaders.put(Elements.ID, subscriptionId);
        return connection.unsubscribe(subscriptionId, stompHeaders)
                .map(f -> (StompFrames) f)
                .future();
    }

    @Override
    public Future<StompFrames> ack(String messageId) {
        return connection.ack(messageId)
                .map(f -> (StompFrames) f)
                .future();
    }

    @Override
    public Future<StompFrames> nack(String messageId) {
        return connection.nack(messageId)
                .map(f -> (StompFrames) f)
                .future();
    }

    @Override
    public Future<StompFrames> disconnect() {
        return connection.disconnect()
                .map(f -> (StompFrames) f)
                .future();
    }

    @Override
    public boolean isConnected() {
        return connection.isConnected();
    }

    private static StompHeaders toHeaders(Map<Elements, String> headers) {
        StompHeaders stompHeaders = StompHeaders.create();
        headers.forEach(stompHeaders::put);
        return stompHeaders;
    }
}
