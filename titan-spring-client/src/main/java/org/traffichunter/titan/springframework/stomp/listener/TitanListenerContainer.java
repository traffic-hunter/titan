package org.traffichunter.titan.springframework.stomp.listener;

import org.jspecify.annotations.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.invocation.HandlerMethodArgumentResolverComposite;
import org.springframework.messaging.handler.invocation.InvocableHandlerMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traffichunter.titan.core.codec.stomp.StompCommand;
import org.traffichunter.titan.core.codec.stomp.StompFrames;
import org.traffichunter.titan.client.TitanClient;
import org.traffichunter.titan.springframework.stomp.core.TitanClientManager;
import org.traffichunter.titan.springframework.stomp.messaging.TitanSpringMessageAdapter;
import org.springframework.util.ErrorHandler;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.TimeUnit;

import static org.traffichunter.titan.core.codec.stomp.StompHeaders.*;

/**
 * Runtime container for a single Titan listener endpoint.
 * Manages subscription lifecycle and invokes the target bean method.
 * Successful listener execution sends ACK when possible; failures call NACK.
 *
 * <p>Each container subscribes within its endpoint's destination group and keeps the identifier
 * the client assigned, so several listeners can share a destination across groups and each one
 * unsubscribes only its own.</p>
 *
 * @author yun
 */
public final class TitanListenerContainer {

    private static final Logger log = LoggerFactory.getLogger(TitanListenerContainer.class);

    private final TitanListenerEndpoint endpoint;
    private final TitanClientManager manager;
    private final HandlerMethodArgumentResolverComposite argumentResolvers;
    private final ErrorHandler listenerErrorHandler;

    private final AtomicBoolean running = new AtomicBoolean(false);

    private volatile @Nullable TitanClient client;
    private volatile @Nullable String subscriptionId;

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

    /**
     * Subscribe to the endpoint destination and start dispatching frames.
     */
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }

        try {
            TitanClient connection = manager.connection();
            this.client = connection;
            String id = connection.subscribe(endpoint.group(), endpoint.destination(), frame -> {
                if (!running.get()) {
                    // A frame already on its way when stop() ran is no longer this listener's.
                    return;
                }
                try {
                    invoke(frame);
                    acknowledgeIfPossible(frame, connection);
                } catch (Exception e) {
                    log.error(
                            "Failed to invoke Titan listener handler. id={}, group={}, destination={}",
                            endpoint.id(),
                            endpoint.group(),
                            endpoint.destination(),
                            e
                    );
                    handleListenerError(e);
                    negativeAcknowledgeIfPossible(frame, connection);
                }
            }).get(manager.connectTimeoutMillis(), TimeUnit.MILLISECONDS);

            this.subscriptionId = id;
            log.info(
                    "Started Titan listener. id={}, group={}, destination={}, subscriptionId={}",
                    endpoint.id(),
                    endpoint.group(),
                    endpoint.destination(),
                    id
            );
        } catch (Exception e) {
            this.client = null;
            this.subscriptionId = null;
            running.set(false);
            throw new IllegalStateException("Failed to start listener " + endpoint.id(), e);
        }
    }

    /**
     * Stop dispatching and unsubscribe from the endpoint destination.
     */
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }

        TitanClient connection = this.client;
        String id = this.subscriptionId;
        this.client = null;
        this.subscriptionId = null;
        if (connection == null || id == null) {
            return;
        }

        try {
            // Asked even when the connection is down. The client then drops the subscription it
            // would otherwise replay, so a reconnect does not bring this listener back.
            CompletableFuture<StompFrames> unsubscribed = connection.unsubscribe(id);
            if (connection.isConnected()) {
                unsubscribed.get(manager.connectTimeoutMillis(), TimeUnit.MILLISECONDS);
            }

            log.info(
                    "Stopped Titan listener. id={}, group={}, destination={}, subscriptionId={}",
                    endpoint.id(),
                    endpoint.group(),
                    endpoint.destination(),
                    id
            );
        } catch (Exception e) {
            throw new IllegalStateException("Failed to stop listener " + endpoint.id(), e);
        }
    }

    /**
     * Return whether this container is currently running.
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Return whether this container is currently stopped.
     */
    public boolean isStopped() {
        return !running.get();
    }

    TitanListenerEndpoint endpoint() {
        return endpoint;
    }

    TitanClientManager manager() {
        return manager;
    }

    /** Identifier the client assigned to this listener's subscription, or {@code null}. */
    public @Nullable String subscriptionId() {
        return subscriptionId;
    }

    HandlerMethodArgumentResolverComposite argumentResolvers() {
        return argumentResolvers;
    }

    ErrorHandler listenerErrorHandler() {
        return listenerErrorHandler;
    }

    private void invoke(StompFrames frame) throws Exception {
        Message<byte[]> springMessage = TitanSpringMessageAdapter.from(frame);

        InvocableHandlerMethod invocable = new InvocableHandlerMethod(endpoint.bean(), endpoint.method());
        invocable.setMessageMethodArgumentResolvers(this.argumentResolvers);
        invocable.invoke(springMessage);
    }

    /**
     * Send ACK for MESSAGE frames that include a message-id.
     */
    private void acknowledgeIfPossible(StompFrames frame, TitanClient connection) {
        if (frame.command() != StompCommand.MESSAGE) {
            return;
        }

        String messageId = frame.getHeader(Elements.MESSAGE_ID);
        if (messageId == null || messageId.isBlank()) {
            log.warn("Skip ACK due to missing message-id header. id={}, destination={}", endpoint.id(), endpoint.destination());
            return;
        }

        connection.ack(messageId);
    }

    /**
     * Send NACK for MESSAGE frames that include a message-id.
     */
    private void negativeAcknowledgeIfPossible(StompFrames frame, TitanClient connection) {
        if (frame.command() != StompCommand.MESSAGE) {
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
