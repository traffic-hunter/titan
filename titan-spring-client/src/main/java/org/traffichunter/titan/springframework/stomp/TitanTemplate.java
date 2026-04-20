package org.traffichunter.titan.springframework.stomp;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import org.traffichunter.titan.core.channel.stomp.StompClientConnection;
import org.traffichunter.titan.core.codec.stomp.StompFrame;
import org.traffichunter.titan.core.util.buffer.Buffer;

public final class TitanTemplate {

    private final TitanClientManager clientManager;

    public TitanTemplate(TitanClientManager clientManager) {
        this.clientManager = clientManager;
    }

    public AsyncTitanTemplate async() {
        return new AsyncTitanTemplate(clientManager);
    }

    public StompFrame send(String destination, String payload) throws Exception {
        return send(destination, payload.getBytes(StandardCharsets.UTF_8));
    }

    public StompFrame send(String destination, ByteBuffer byteBuffer) throws Exception {
        ByteBuffer buf = byteBuffer.slice();
        byte[] bytes = new byte[buf.remaining()];
        buf.get(bytes);
        return send(destination, bytes);
    }

    public StompFrame send(String destination, byte[] payload) throws Exception {
        StompClientConnection connection = getConnection();
        return connection.send(destination, Buffer.alloc(payload)).get();
    }

    public StompFrame subscribe(String destination) throws Exception {
        StompClientConnection connection = getConnection();
        return connection.subscribe(destination).get();
    }

    public StompFrame unsubscribe(String destination) throws Exception {
        StompClientConnection connection = getConnection();
        return connection.unsubscribe(destination).get();
    }

    public StompFrame disconnect() throws Exception {
        StompClientConnection connection = getConnection();
        return connection.disconnect().get();
    }

    private StompClientConnection getConnection() throws Exception {
        return clientManager.connection();
    }

    public static class AsyncTitanTemplate {

        private final TitanClientManager clientManager;

        private AsyncTitanTemplate(TitanClientManager clientManager) {
            this.clientManager = clientManager;
        }

        public Future<StompFrame> send(String destination, String payload) throws Exception {
            return send(destination, payload.getBytes(StandardCharsets.UTF_8));
        }

        public Future<StompFrame> send(String destination, ByteBuffer byteBuffer) throws Exception {
            ByteBuffer buf = byteBuffer.slice();
            byte[] bytes = new byte[buf.remaining()];
            buf.get(bytes);
            return send(destination, bytes);
        }

        public Future<StompFrame> send(String destination, byte[] payload) throws Exception {
            StompClientConnection connection = getConnection();
            return connection.send(destination, Buffer.alloc(payload)).future();
        }

        public Future<StompFrame> subscribe(String destination) throws Exception {
            StompClientConnection connection = getConnection();
            return connection.subscribe(destination).future();
        }

        public Future<StompFrame> unsubscribe(String destination) throws Exception {
            StompClientConnection connection = getConnection();
            return connection.unsubscribe(destination).future();
        }

        public Future<StompFrame> disconnect() throws Exception {
            StompClientConnection connection = getConnection();
            return connection.disconnect().future();
        }

        private StompClientConnection getConnection() throws Exception {
            return clientManager.connection();
        }
    }
}
