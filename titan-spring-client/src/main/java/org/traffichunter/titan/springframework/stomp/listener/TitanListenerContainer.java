package org.traffichunter.titan.springframework.stomp.listener;

import org.springframework.messaging.Message;
import org.springframework.messaging.handler.invocation.HandlerMethodArgumentResolverComposite;
import org.springframework.messaging.handler.invocation.InvocableHandlerMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traffichunter.titan.core.channel.stomp.StompClientConnection;
import org.traffichunter.titan.core.codec.stomp.StompCommand;
import org.traffichunter.titan.core.codec.stomp.StompFrame;
import org.traffichunter.titan.springframework.stomp.TitanClientManager;
import org.traffichunter.titan.springframework.stomp.messaging.TitanSpringMessageAdapter;
import org.springframework.util.ErrorHandler;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.traffichunter.titan.core.codec.stomp.StompHeaders.*;

public final class TitanListenerContainer {

    private static final Logger log = LoggerFactory.getLogger(TitanListenerContainer.class);

    private final TitanListenerEndpoint endpoint;
    private final TitanClientManager manager;
    private final HandlerMethodArgumentResolverComposite argumentResolvers;
    private final ErrorHandler listenerErrorHandler;

    private final AtomicBoolean running = new AtomicBoolean(false);

    public TitanListenerContainer(
            TitanListenerEndpoint endpoint,
            TitanClientManager manager,
            HandlerMethodArgumentResolverComposite argumentResolvers,
            ErrorHandler listenerErrorHandler
    ) {
        this.endpoint = endpoint;
        this.manager = manager;
        this.argumentResolvers = argumentResolvers;
        this.listenerErrorHandler = listenerErrorHandler;
    }

    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }

        try {
            StompClientConnection conn = manager.connection();
            conn.subscribe(endpoint.destination(), frame -> {
                try {
                    invoke(frame);
                    acknowledgeIfPossible(frame, conn);
                } catch (Exception e) {
                    log.error(
                            "Failed to invoke Titan listener handler. id={}, destination={}",
                            endpoint.id(),
                            endpoint.destination(),
                            e
                    );
                    handleListenerError(e);
                    negativeAcknowledgeIfPossible(frame, conn);
                }
            });
            log.info("Started Titan listener. id={}, destination={}", endpoint.id(), endpoint.destination());
        } catch (Exception e) {
            running.set(false);
            throw new IllegalStateException("Failed to start listener " + endpoint.id(), e);
        }
    }

    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }

        try {
            StompClientConnection conn = manager.currentConnection();
            if(conn == null) {
                return;
            }

            if (conn.isConnected()) {
                conn.unsubscribe(endpoint.destination());
            }

            log.info("Stopped Titan listener. id={}, destination={}", endpoint.id(), endpoint.destination());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to stop listener " + endpoint.id(), e);
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    public boolean isStopped() {
        return !running.get();
    }

    private void invoke(StompFrame frame) throws Exception {
        Message<byte[]> springMessage = TitanSpringMessageAdapter.from(frame);

        InvocableHandlerMethod invocable = new InvocableHandlerMethod(endpoint.bean(), endpoint.method());
        invocable.setMessageMethodArgumentResolvers(this.argumentResolvers);
        invocable.invoke(springMessage);
    }

    private void acknowledgeIfPossible(StompFrame frame, StompClientConnection connection) {
        if (frame.getCommand() != StompCommand.MESSAGE) {
            return;
        }

        String messageId = frame.getHeader(Elements.MESSAGE_ID);
        if (messageId == null || messageId.isBlank()) {
            log.warn("Skip ACK due to missing message-id header. id={}, destination={}", endpoint.id(), endpoint.destination());
            return;
        }

        connection.ack(messageId);
    }

    private void negativeAcknowledgeIfPossible(StompFrame frame, StompClientConnection connection) {
        if (frame.getCommand() != StompCommand.MESSAGE) {
            return;
        }

        String messageId = frame.getHeader(Elements.MESSAGE_ID);
        if (messageId == null || messageId.isBlank()) {
            log.warn("Skip NACK due to missing message-id header. id={}, destination={}", endpoint.id(), endpoint.destination());
            return;
        }

        connection.nack(messageId);
    }

    private void handleListenerError(Throwable error) {
        try {
            listenerErrorHandler.handleError(error);
        } catch (Throwable handlerError) {
            log.warn("Titan listener error handler failed. id={}, destination={}",
                    endpoint.id(),
                    endpoint.destination(),
                    handlerError);
        }
    }
}
