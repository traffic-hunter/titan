package org.traffichunter.titan.springframework.stomp.core;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.traffichunter.titan.core.codec.stomp.StompFrames;
import org.traffichunter.titan.core.util.Handler;
import org.traffichunter.titan.core.util.buffer.Buffer;

import static org.traffichunter.titan.core.codec.stomp.StompHeaders.*;

/**
 * Spring-facing STOMP operations implemented by {@link TitanTemplate}.
 *
 * @author yun
 */
public interface StompOperations {

    /**
     * Sends a message and transfers ownership of {@code payload} to this operation.
     *
     * <p>Exactly one buffer reference is consumed on success or failure. The caller must not
     * release or reuse the payload after invocation.</p>
     *
     * @param destination target STOMP destination
     * @param payload payload whose ownership is transferred
     * @return asynchronous send result
     */
    CompletableFuture<StompFrames> send(String destination, Buffer payload);

    /**
     * Sends a message with headers using the same ownership contract as
     * {@link #send(String, Buffer)}.
     *
     * @param destination target STOMP destination
     * @param payload payload whose ownership is transferred
     * @param headers additional STOMP headers
     * @return asynchronous send result
     */
    CompletableFuture<StompFrames> send(String destination, Buffer payload, Map<Elements, String> headers);

    CompletableFuture<String> subscribe(String destination, Handler<StompFrames> handler);

    CompletableFuture<String> subscribe(String destination, Map<Elements, String> headers, Handler<StompFrames> handler);

    CompletableFuture<StompFrames> unsubscribe(String subscriptionId);

    CompletableFuture<StompFrames> unsubscribe(String subscriptionId, Map<Elements, String> headers);

    CompletableFuture<StompFrames> ack(String messageId);

    CompletableFuture<StompFrames> nack(String messageId);

    CompletableFuture<StompFrames> disconnect();
}
