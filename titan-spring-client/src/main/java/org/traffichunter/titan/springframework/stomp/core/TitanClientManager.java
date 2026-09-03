package org.traffichunter.titan.springframework.stomp.core;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.traffichunter.titan.client.TitanClient;
import org.traffichunter.titan.springframework.stomp.TitanProperties;

import java.util.concurrent.TimeUnit;

/**
 * Spring lifecycle adapter for a Titan STOMP client.
 * Starts and stops the underlying client with the application context and
 * resolves active STOMP connections for template and listener use.
 *
 * <p>The underlying {@link TitanClient} owns lifecycle state
 * ({@link TitanClient#isStarted()} / {@link TitanClient#isShutdown()}); this
 * adapter does not maintain a separate copy of that state.
 *
 * @author yun
 */
public final class TitanClientManager implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(TitanClientManager.class);

    private static final long DEFAULT_TIMEOUT = 30;
    public static final int PHASE = Integer.MAX_VALUE - 100;

    private final TitanClient stompClient;
    private final TitanProperties properties;

    public TitanClientManager(TitanClient stompClient, TitanProperties properties) {
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

    public TitanClient connection() throws Exception {
        if (isConnected()) {
            return stompClient;
        }

        return connect();
    }

    public long connectTimeoutMillis() {
        return properties.getConnectTimeoutMillis();
    }

    public @Nullable TitanClient currentConnection() {
        return isConnected() ? stompClient : null;
    }

    public boolean isConnected() {
        try {
            return stompClient.isConnected();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    private TitanClient connect() throws Exception {
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
