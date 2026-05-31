package org.traffichunter.titan.springframework.stomp;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.traffichunter.titan.core.resilience.retry.RetryExecutor;
import org.traffichunter.titan.core.resilience.retry.RetryExecutors;
import org.traffichunter.titan.core.resilience.retry.RetryListener;
import org.traffichunter.titan.core.transport.stomp.client.StompClient;
import org.traffichunter.titan.core.transport.stomp.client.StompClientOperations;

/**
 * Spring lifecycle adapter for a Titan STOMP client.
 * Starts and stops the underlying client with the application context.
 * Resolves or creates active STOMP operations for template and listener use.
 *
 * @author yun
 */
public final class TitanClientManager implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(TitanClientManager.class);

    private static final long DEFAULT_TIMEOUT = 30;
    public static final int PHASE = Integer.MAX_VALUE - 100;

    private final StompClient stompClient;
    private final TitanProperties properties;
    private final @Nullable RetryExecutor retryExecutor;

    private final AtomicBoolean running = new AtomicBoolean(false);

    public TitanClientManager(StompClient stompClient, TitanProperties properties) {
        this.stompClient = stompClient;
        this.properties = properties;

        TitanProperties.Retry retry = properties.getRetry();
        if (retry.isEnabled()) {
            this.retryExecutor = RetryExecutors.eventLoopRetryExecutor(retry.toPolicy(), new LoggingRetryListener());
        } else {
            this.retryExecutor = null;
        }
    }

    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }

        try {
            if (!stompClient.isStarted()) {
                stompClient.start();
            }
            if (properties.isAutoConnect()) {
                if (retryExecutor == null) {
                    connect();
                } else {
                    try {
                        connect();
                    } catch (Exception e) {
                        log.warn("Failed to connect Titan STOMP client. Scheduling retry.", e);
                        retryExecutor.retry(this::connect);
                    }
                }
            }
            log.info("Started Titan Client Manager");
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
            stompClient.shutdown(DEFAULT_TIMEOUT, TimeUnit.SECONDS);
            if (retryExecutor != null) {
                retryExecutor.shutdown(DEFAULT_TIMEOUT, TimeUnit.SECONDS);
            }
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
        return running.get();
    }

    @Override
    public boolean isAutoStartup() {
        return properties.isAutoStart();
    }

    @Override
    public int getPhase() {
        return PHASE;
    }

    public StompClientOperations connect() throws Exception {
        if (!stompClient.isStarted()) {
            stompClient.start();
        }

        return stompClient.connect()
                .get(properties.getConnectTimeoutMillis(), TimeUnit.MILLISECONDS);
    }

    public StompClientOperations operations() throws Exception {
        if (isConnected()) {
            return stompClient.operations();
        }
        return connect();
    }

    public long connectTimeoutMillis() {
        return properties.getConnectTimeoutMillis();
    }

    public @Nullable StompClientOperations currentOperations() {
        try {
            return stompClient.operations();
        } catch (IllegalStateException e) {
            return null;
        }
    }

    public boolean isConnected() {
        try {
            return stompClient.operations().isConnected();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    private void reconnect() {

    }

    private static final class LoggingRetryListener implements RetryListener {

        @Override
        public void onRetry(int attempt, Duration delay) {
            log.warn("Retry scheduled attempt #{}, delay={}", attempt, delay);
        }

        @Override
        public void onRetryFailed(int attempt, Throwable cause) {
            log.warn("Retry failed attempt #{}, cause={}", attempt, cause.getMessage());
        }

        @Override
        public void onRetryExhausted(int attempt, Throwable cause) {
            log.warn("Retry exhausted attempt #{}, cause={}", attempt, cause.getMessage());
        }
    }
}
