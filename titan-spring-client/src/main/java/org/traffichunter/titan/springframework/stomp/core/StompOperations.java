package org.traffichunter.titan.springframework.stomp.core;

import java.util.Map;
import java.util.concurrent.Future;

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

    Future<StompFrames> send(String destination, Buffer payload);

    Future<StompFrames> send(String destination, Buffer payload, Map<Elements, String> headers);

    Future<String> subscribe(String destination, Handler<StompFrames> handler);

    Future<String> subscribe(String destination, Map<Elements, String> headers, Handler<StompFrames> handler);

    Future<StompFrames> unsubscribe(String subscriptionId);

    Future<StompFrames> unsubscribe(String subscriptionId, Map<Elements, String> headers);

    Future<StompFrames> ack(String messageId);

    Future<StompFrames> nack(String messageId);

    Future<StompFrames> disconnect();
}
