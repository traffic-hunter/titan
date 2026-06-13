package org.traffichunter.titan.springframework.stomp.core;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.traffichunter.titan.core.transport.stomp.client.StompClient;
import org.traffichunter.titan.core.transport.stomp.client.StompConnection;
import org.traffichunter.titan.springframework.stomp.TitanProperties;

import java.util.concurrent.TimeUnit;

/**
 * Spring lifecycle adapter for a Titan STOMP client.
 * Starts and stops the underlying client with the application context and
 * resolves active STOMP connections for template and listener use.
 *
 * <p>Lifecycle state is owned entirely by the underlying {@link StompClient}
 * ({@link StompClient#isStarted()} / {@link StompClient#isShutdown()}); this
 * adapter keeps no parallel status to avoid two sources of truth that can drift.
 *
 * @author yun
 */
public final class TitanClientManager implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(TitanClientManager.class);

    private static final long DEFAULT_TIMEOUT = 30;
    public static final int PHASE = Integer.MAX_VALUE - 100;

    private final StompClient stompClient;
    private final TitanProperties properties;

    public TitanClientManager(StompClient stompClient, TitanProperties properties) {
        this.stompClient = stompClient;
        this.properties = properties;
    }

    @Override
    public void start() {
        if (stompClient.isStarted() || stompClient.isShutdown()) {
            return;
        }

        try {
            stompClient.start();
            if (properties.isAutoConnect()) {
                connect();
            }
            log.info("Started Titan Client Manager");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to start Titan STOMP client manager", e);
        }
    }

    @Override
    public void stop() {
        if (!isRunning()) {
            return;
        }

        try {
            stompClient.shutdown(DEFAULT_TIMEOUT, TimeUnit.SECONDS);
            log.info("Shutting down Titan STOMP client manager");
        } catch (Exception e) {
            log.warn("Failed to shutdown STOMP client cleanly", e);
        }
    }

    @Override
    public void stop(Runnable callback) {
        stop();
        callback.run();
    }

    @Override
    public boolean isRunning() {
        return stompClient.isStarted() && !stompClient.isShutdown();
    }

    @Override
    public boolean isAutoStartup() {
        return properties.isAutoStart();
    }

    @Override
    public int getPhase() {
        return PHASE;
    }

    public StompConnection connection() throws Exception {
        if (isConnected()) {
            return stompClient.connection();
        }

        return connect();
    }

    public long connectTimeoutMillis() {
        return properties.getConnectTimeoutMillis();
    }

    public @Nullable StompConnection currentConnection() {
        try {
            return stompClient.connection();
        } catch (IllegalStateException e) {
            return null;
        }
    }

    public boolean isConnected() {
        try {
            return stompClient.connection().isConnected();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    private StompConnection connect() throws Exception {
        if (stompClient.isShutdown()) {
            throw new IllegalStateException("Titan STOMP client manager has been shut down");
        }
        if (!stompClient.isStarted()) {
            stompClient.start();
        }

        return stompClient.connect()
                .get(properties.getConnectTimeoutMillis(), TimeUnit.MILLISECONDS);
    }
}
