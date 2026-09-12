package org.traffichunter.titan.springframework.stomp.core;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.traffichunter.titan.core.codec.stomp.StompFrames;
import org.traffichunter.titan.core.codec.stomp.StompHeaders.Elements;
import org.traffichunter.titan.client.TitanClient;
import org.traffichunter.titan.core.util.Handler;
import org.traffichunter.titan.core.util.buffer.Buffer;

/**
 * Default {@link StompOperations} implementation.
 * Delegates every operation to the active {@link TitanClient} resolved through
 * {@link TitanClientManager}, connecting on demand when no connection exists yet.
 *
 * <p>{@link StompOperations} methods return {@link CompletableFuture} results.
 * The blocking send and subscribe overloads wait up to
 * {@link TitanClientManager#connectTimeoutMillis()}.
 *
 * @author yun
 */
public final class TitanTemplate implements StompOperations {

    private static final Handler<StompFrames> NOOP_HANDLER = frame -> { };

    private final TitanClientManager clientManager;

    public TitanTemplate(TitanClientManager clientManager) {
        this.clientManager = clientManager;
    }

    @Override
    public CompletableFuture<StompFrames> send(String destination, Buffer payload) {
        TitanClient client;
        try {
            client = connection();
        } catch (RuntimeException error) {
            payload.release();
            throw error;
        }
        return client.send(destination, payload);
    }

    @Override
    public CompletableFuture<StompFrames> send(String destination, Buffer payload, Map<Elements, String> headers) {
        TitanClient client;
        try {
            client = connection();
        } catch (RuntimeException error) {
            payload.release();
            throw error;
        }
        return client.send(destination, payload, headers);
    }

    @Override
    public CompletableFuture<StompFrames> send(String group, String destination, Buffer payload) {
        TitanClient client;
        try {
            client = connection();
        } catch (RuntimeException error) {
            payload.release();
            throw error;
        }
        return client.send(group, destination, payload);
    }

    @Override
    public CompletableFuture<StompFrames> send(
            String group,
            String destination,
            Buffer payload,
            Map<Elements, String> headers
    ) {
        TitanClient client;
        try {
            client = connection();
        } catch (RuntimeException error) {
            payload.release();
            throw error;
        }
        return client.send(group, destination, payload, headers);
    }

    @Override
    public CompletableFuture<String> subscribe(String destination, Handler<StompFrames> handler) {
        return connection().subscribe(destination, handler);
    }

    @Override
    public CompletableFuture<String> subscribe(String group, String destination, Handler<StompFrames> handler) {
        return connection().subscribe(group, destination, handler);
    }

    @Override
    public CompletableFuture<String> subscribe(
            String group,
            String destination,
            Map<Elements, String> headers,
            Handler<StompFrames> handler
    ) {
        return connection().subscribe(group, destination, headers, handler);
    }

    @Override
    public CompletableFuture<String> subscribe(String destination, Map<Elements, String> headers, Handler<StompFrames> handler) {
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
        return await(send(destination, Buffer.heap().alloc(payload)));
    }

    public String subscribe(String destination) throws Exception {
        return await(subscribe(destination, NOOP_HANDLER));
    }

    /**
     * Sends a UTF-8 string payload to a destination group and waits for the transport result.
     *
     * @param group destination group; blank means the default group
     * @param destination target STOMP destination
     * @param payload string payload
     * @return the resulting transport frame
     * @throws Exception when the send fails or the wait times out
     */
    public StompFrames send(String group, String destination, String payload) throws Exception {
        return send(group, destination, payload.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Sends the remaining bytes of {@code byteBuffer} to a destination group.
     *
     * @param group destination group; blank means the default group
     * @param destination target STOMP destination
     * @param byteBuffer payload source, left with its position unchanged
     * @return the resulting transport frame
     * @throws Exception when the send fails or the wait times out
     */
    public StompFrames send(String group, String destination, ByteBuffer byteBuffer) throws Exception {
        ByteBuffer copied = byteBuffer.slice();
        byte[] payload = new byte[copied.remaining()];
        copied.get(payload);
        return send(group, destination, payload);
    }

    /**
     * Sends a byte payload to a destination group and waits for the transport result.
     *
     * @param group destination group; blank means the default group
     * @param destination target STOMP destination
     * @param payload payload bytes
     * @return the resulting transport frame
     * @throws Exception when the send fails or the wait times out
     */
    public StompFrames send(String group, String destination, byte[] payload) throws Exception {
        return await(send(group, destination, Buffer.heap().alloc(payload)));
    }

    private <T> T await(CompletableFuture<T> future) throws Exception {
        return future.get(clientManager.connectTimeoutMillis(), TimeUnit.MILLISECONDS);
    }

    private TitanClient connection() {
        try {
            return clientManager.connection();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to resolve active STOMP connection", e);
        }
    }
}
