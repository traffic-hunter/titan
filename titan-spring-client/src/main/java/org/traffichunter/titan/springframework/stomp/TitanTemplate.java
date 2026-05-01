package org.traffichunter.titan.springframework.stomp;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.traffichunter.titan.core.channel.stomp.StompClientConnection;
import org.traffichunter.titan.core.codec.stomp.StompFrame;
import org.traffichunter.titan.core.codec.stomp.StompHeaders;
import org.traffichunter.titan.core.util.buffer.Buffer;
import static org.traffichunter.titan.core.codec.stomp.StompHeaders.Elements.ID;

/**
 * Convenience operations for sending and managing STOMP subscriptions.
 * Uses {@link TitanClientManager} to resolve the active connection.
 * Provides synchronous methods and an async view backed by Titan promises.
 *
 * @author yun
 */
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
        ByteBuffer copied = byteBuffer.slice();
        byte[] payload = new byte[copied.remaining()];
        copied.get(payload);
        return send(destination, payload);
    }

    public StompFrame send(String destination, byte[] payload) throws Exception {
        StompClientConnection connection = resolveConnection();
        return connection.send(destination, Buffer.alloc(payload))
                .get(clientManager.connectTimeoutMillis(), TimeUnit.MILLISECONDS);
    }

    public StompFrame subscribe(String destination) throws Exception {
        StompClientConnection connection = resolveConnection();
        return connection.subscribe(destination)
                .get(clientManager.connectTimeoutMillis(), TimeUnit.MILLISECONDS);
    }

    public StompFrame unsubscribe(String destination) throws Exception {
        StompClientConnection connection = resolveConnection();
        StompHeaders headers = StompHeaders.create();
        headers.put(ID, destination);
        return connection.unsubscribe(destination, headers)
                .get(clientManager.connectTimeoutMillis(), TimeUnit.MILLISECONDS);
    }

    public StompFrame disconnect() throws Exception {
        StompClientConnection connection = resolveConnection();
        return connection.disconnect()
                .get(clientManager.connectTimeoutMillis(), TimeUnit.MILLISECONDS);
    }

    private StompClientConnection resolveConnection() throws Exception {
        return clientManager.connection();
    }

    /**
     * Async variant of {@link TitanTemplate} that exposes {@link Future} results.
     *
     * @author yun
     */
    public static final class AsyncTitanTemplate {

        private final TitanClientManager clientManager;

        private AsyncTitanTemplate(TitanClientManager clientManager) {
            this.clientManager = clientManager;
        }

        public Future<StompFrame> send(String destination, String payload) throws Exception {
            return send(destination, payload.getBytes(StandardCharsets.UTF_8));
        }

        public Future<StompFrame> send(String destination, ByteBuffer byteBuffer) throws Exception {
            ByteBuffer copied = byteBuffer.slice();
            byte[] payload = new byte[copied.remaining()];
            copied.get(payload);
            return send(destination, payload);
        }

        public Future<StompFrame> send(String destination, byte[] payload) throws Exception {
            StompClientConnection connection = resolveConnection();
            return connection.send(destination, Buffer.alloc(payload)).future();
        }

        public Future<StompFrame> subscribe(String destination) throws Exception {
            StompClientConnection connection = resolveConnection();
            return connection.subscribe(destination).future();
        }

        public Future<StompFrame> unsubscribe(String destination) throws Exception {
            StompClientConnection connection = resolveConnection();
            StompHeaders headers = StompHeaders.create();
            headers.put(ID, destination);
            return connection.unsubscribe(destination, headers).future();
        }

        public Future<StompFrame> disconnect() throws Exception {
            StompClientConnection connection = resolveConnection();
            return connection.disconnect().future();
        }

        private StompClientConnection resolveConnection() throws Exception {
            return clientManager.connection();
        }
    }
}
