package org.traffichunter.titan.springframework.stomp;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.traffichunter.titan.core.channel.stomp.StompClientConnection;
import org.traffichunter.titan.core.transport.stomp.StompClient;

/**
 * Spring lifecycle adapter for a Titan STOMP client.
 * Starts and stops the underlying client with the application context.
 * Resolves or creates the active STOMP connection for template and listener use.
 *
 * @author yun
 */
public final class TitanClientManager implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(TitanClientManager.class);

    private final StompClient stompClient;
    private final TitanProperties properties;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public TitanClientManager(StompClient stompClient, TitanProperties properties) {
        this.stompClient = stompClient;
        this.properties = properties;
    }

    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }

        try {
            if (!stompClient.isStart()) {
                stompClient.start();
            }
            if (properties.isAutoConnect()) {
                connect();
            }
        } catch (Exception e) {
            running.set(false);
            throw new IllegalStateException("Failed to start Titan STOMP client manager", e);
        }
    }

    @Override
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        try {
            stompClient.shutdown(30, TimeUnit.SECONDS);
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
        return running.get();
    }

    @Override
    public boolean isAutoStartup() {
        return properties.isAutoStart();
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    public StompClientConnection connect() throws Exception {
        return stompClient
                .connect(properties.getHost(), properties.getPort(), properties.getConnectTimeoutMillis(), TimeUnit.MILLISECONDS)
                .get(properties.getConnectTimeoutMillis(), TimeUnit.MILLISECONDS);
    }

    public StompClientConnection connection() throws Exception {
        if (isConnected()) {
            return stompClient.connection();
        }
        return connect();
    }

    public long connectTimeoutMillis() {
        return properties.getConnectTimeoutMillis();
    }

    public @Nullable StompClientConnection currentConnection() {
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
}
