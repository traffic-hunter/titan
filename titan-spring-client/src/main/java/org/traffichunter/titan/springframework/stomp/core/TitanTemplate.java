package org.traffichunter.titan.springframework.stomp.core;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.traffichunter.titan.core.codec.stomp.StompFrames;
import org.traffichunter.titan.core.codec.stomp.StompHeaders.Elements;
import org.traffichunter.titan.core.transport.stomp.client.StompConnection;
import org.traffichunter.titan.core.util.Handler;
import org.traffichunter.titan.core.util.buffer.Buffer;

/**
 * Default {@link StompOperations} implementation.
 * Delegates every operation to the active {@link StompConnection} resolved through
 * {@link TitanClientManager}, connecting on demand when no connection exists yet.
 *
 * <p>The {@link StompOperations} contract is asynchronous and returns {@link Future}
 * results. Blocking convenience overloads are provided for the common send and
 * subscribe calls and wait up to {@link TitanClientManager#connectTimeoutMillis()}.
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
    public Future<StompFrames> send(String destination, Buffer payload) {
        return connection().send(destination, payload);
    }

    @Override
    public Future<StompFrames> send(String destination, Buffer payload, Map<Elements, String> headers) {
        return connection().send(destination, payload, headers);
    }

    @Override
    public Future<String> subscribe(String destination, Handler<StompFrames> handler) {
        return connection().subscribe(destination, handler);
    }

    @Override
    public Future<String> subscribe(String destination, Map<Elements, String> headers, Handler<StompFrames> handler) {
        return connection().subscribe(destination, headers, handler);
    }

    @Override
    public Future<StompFrames> unsubscribe(String subscriptionId) {
        return connection().unsubscribe(subscriptionId);
    }

    @Override
    public Future<StompFrames> unsubscribe(String subscriptionId, Map<Elements, String> headers) {
        return connection().unsubscribe(subscriptionId, headers);
    }

    @Override
    public Future<StompFrames> ack(String messageId) {
        return connection().ack(messageId);
    }

    @Override
    public Future<StompFrames> nack(String messageId) {
        return connection().nack(messageId);
    }

    @Override
    public Future<StompFrames> disconnect() {
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
        return await(send(destination, Buffer.alloc(payload)));
    }

    public String subscribe(String destination) throws Exception {
        return await(subscribe(destination, NOOP_HANDLER));
    }

    private <T> T await(Future<T> future) throws Exception {
        return future.get(clientManager.connectTimeoutMillis(), TimeUnit.MILLISECONDS);
    }

    private StompConnection connection() {
        try {
            return clientManager.connection();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to resolve active STOMP connection", e);
        }
    }
}
