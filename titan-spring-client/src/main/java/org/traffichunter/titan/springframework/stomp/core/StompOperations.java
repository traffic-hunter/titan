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

    CompletableFuture<StompFrames> send(String destination, Buffer payload);

    CompletableFuture<StompFrames> send(String destination, Buffer payload, Map<Elements, String> headers);

    CompletableFuture<String> subscribe(String destination, Handler<StompFrames> handler);

    CompletableFuture<String> subscribe(String destination, Map<Elements, String> headers, Handler<StompFrames> handler);

    CompletableFuture<StompFrames> unsubscribe(String subscriptionId);

    CompletableFuture<StompFrames> unsubscribe(String subscriptionId, Map<Elements, String> headers);

    CompletableFuture<StompFrames> ack(String messageId);

    CompletableFuture<StompFrames> nack(String messageId);

    CompletableFuture<StompFrames> disconnect();
}
