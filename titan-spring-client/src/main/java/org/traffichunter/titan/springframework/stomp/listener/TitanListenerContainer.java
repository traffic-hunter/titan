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
 * <p>A SUBSCRIBE takes time to complete, and a stop or a start timeout can land while one is on
 * the wire. Each start therefore carries a generation, and an identifier that arrives once its
 * generation is over belongs to nobody: the container releases it instead of storing it, so a
 * stopped listener never leaves a subscription behind.</p>
 *
 * @author yun
 */
public final class TitanListenerContainer {

    private static final Logger log = LoggerFactory.getLogger(TitanListenerContainer.class);

    private final TitanListenerEndpoint endpoint;
    private final TitanClientManager manager;
    private final HandlerMethodArgumentResolverComposite argumentResolvers;
    private final ErrorHandler listenerErrorHandler;

    private final Object lifecycle = new Object();

    private volatile boolean running;
    private long generation;
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
        long token;
        synchronized (lifecycle) {
            if (running) {
                return;
            }
            running = true;
            token = ++generation;
        }

        TitanClient connection = null;
        CompletableFuture<String> subscribing = null;
        try {
            connection = manager.connection();
            this.client = connection;
            TitanClient current = connection;
            subscribing = current.subscribe(endpoint.group(), endpoint.destination(), frame -> {
                if (!running) {
                    // A frame already on its way when stop() ran is no longer this listener's.
                    return;
                }
                try {
                    invoke(frame);
                    acknowledgeIfPossible(frame, current);
                } catch (Exception e) {
                    log.error(
                            "Failed to invoke Titan listener handler. id={}, group={}, destination={}",
                            endpoint.id(),
                            endpoint.group(),
                            endpoint.destination(),
                            e
                    );
                    handleListenerError(e);
                    negativeAcknowledgeIfPossible(frame, current);
                }
            });

            String id = subscribing.get(manager.connectTimeoutMillis(), TimeUnit.MILLISECONDS);
            if (!claim(token, id)) {
                // The container was stopped, or started again, while this SUBSCRIBE was on the
                // wire. Nobody holds this identifier now, so this is the only chance to give the
                // subscription back.
                release(current, id);
                return;
            }

            log.info(
                    "Started Titan listener. id={}, group={}, destination={}, subscriptionId={}",
                    endpoint.id(),
                    endpoint.group(),
                    endpoint.destination(),
                    id
            );
        } catch (Exception e) {
            abandon(token);
            if (subscribing != null) {
                // A SUBSCRIBE that succeeds after its timeout still creates a subscription on the
                // server, and this container is no longer the one holding it.
                TitanClient late = connection;
                subscribing.thenAccept(id -> release(late, id));
            }
            throw new IllegalStateException("Failed to start listener " + endpoint.id(), e);
        }
    }

    /**
     * Stop dispatching and unsubscribe from the endpoint destination.
     */
    public void stop() {
        TitanClient connection;
        String id;
        synchronized (lifecycle) {
            if (!running) {
                return;
            }
            running = false;
            // Ends the generation of a start that is still waiting for its identifier, so it
            // releases that subscription rather than storing it here.
            generation++;
            connection = this.client;
            id = this.subscriptionId;
            this.client = null;
            this.subscriptionId = null;
        }
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
        return running;
    }

    /**
     * Return whether this container is currently stopped.
     */
    public boolean isStopped() {
        return !running;
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

    /**
     * Stores the identifier when this start is still the one that owns the container.
     *
     * @return {@code false} when the container was stopped or started again in the meantime
     */
    private boolean claim(long token, String id) {
        synchronized (lifecycle) {
            if (!running || generation != token) {
                return false;
            }
            this.subscriptionId = id;
            return true;
        }
    }

    /** Clears the container's state unless a later start already took it over. */
    private void abandon(long token) {
        synchronized (lifecycle) {
            if (generation != token) {
                return;
            }
            running = false;
            this.client = null;
            this.subscriptionId = null;
        }
    }

    /** Gives back a subscription this container no longer holds, without waiting for the frame. */
    private void release(@Nullable TitanClient connection, @Nullable String id) {
        if (connection == null || id == null) {
            return;
        }
        try {
            connection.unsubscribe(id);
            log.info(
                    "Released a Titan subscription that outlived its listener. id={}, subscriptionId={}",
                    endpoint.id(),
                    id
            );
        } catch (RuntimeException e) {
            log.warn(
                    "Failed to release a Titan subscription that outlived its listener. id={}, subscriptionId={}",
                    endpoint.id(),
                    id,
                    e
            );
        }
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
