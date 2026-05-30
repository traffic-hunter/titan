package org.traffichunter.titan.springframework.stomp;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.traffichunter.titan.core.codec.stomp.StompFrames;
import org.traffichunter.titan.core.transport.stomp.client.StompClientOperations;
import org.traffichunter.titan.core.util.buffer.Buffer;
import static org.traffichunter.titan.core.codec.stomp.StompHeaders.Elements.ID;

/**
 * Convenience operations for sending and managing STOMP subscriptions.
 * Uses {@link TitanClientManager} to resolve the active connection.
 * Provides synchronous methods and an async view backed by the configured STOMP client.
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

    public StompFrames send(String destination, String payload) throws Exception {
        return send(destination, payload.getBytes(StandardCharsets.UTF_8));
    }

    public StompFrames send(String destination, ByteBuffer byteBuffer) throws Exception {
        ByteBuffer copied = byteBuffer.slice();
        byte[] payload = new byte[copied.remaining()];
        copied.get(payload);
        return send(destination, payload);
    }

    public StompFrames send(String destination, byte[] payload) throws Exception {
        StompClientOperations operations = resolveOperations();
        return operations.send(destination, Buffer.alloc(payload))
                .get(clientManager.connectTimeoutMillis(), TimeUnit.MILLISECONDS);
    }

    public String subscribe(String destination) throws Exception {
        StompClientOperations operations = resolveOperations();
        return operations.subscribe(destination, frame -> { })
                .get(clientManager.connectTimeoutMillis(), TimeUnit.MILLISECONDS);
    }

    public StompFrames unsubscribe(String destination) throws Exception {
        StompClientOperations operations = resolveOperations();
        return operations.unsubscribe(destination, java.util.Map.of(ID, destination))
                .get(clientManager.connectTimeoutMillis(), TimeUnit.MILLISECONDS);
    }

    public StompFrames disconnect() throws Exception {
        StompClientOperations operations = resolveOperations();
        return operations.disconnect()
                .get(clientManager.connectTimeoutMillis(), TimeUnit.MILLISECONDS);
    }

    private StompClientOperations resolveOperations() throws Exception {
        return clientManager.operations();
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

        public Future<StompFrames> send(String destination, String payload) throws Exception {
            return send(destination, payload.getBytes(StandardCharsets.UTF_8));
        }

        public Future<StompFrames> send(String destination, ByteBuffer byteBuffer) throws Exception {
            ByteBuffer copied = byteBuffer.slice();
            byte[] payload = new byte[copied.remaining()];
            copied.get(payload);
            return send(destination, payload);
        }

        public Future<StompFrames> send(String destination, byte[] payload) throws Exception {
            StompClientOperations operations = resolveOperations();
            return operations.send(destination, Buffer.alloc(payload));
        }

        public Future<String> subscribe(String destination) throws Exception {
            StompClientOperations operations = resolveOperations();
            return operations.subscribe(destination, frame -> { });
        }

        public Future<StompFrames> unsubscribe(String destination) throws Exception {
            StompClientOperations operations = resolveOperations();
            return operations.unsubscribe(destination, java.util.Map.of(ID, destination));
        }

        public Future<StompFrames> disconnect() throws Exception {
            StompClientOperations operations = resolveOperations();
            return operations.disconnect();
        }

        private StompClientOperations resolveOperations() throws Exception {
            return clientManager.operations();
        }
    }
}
