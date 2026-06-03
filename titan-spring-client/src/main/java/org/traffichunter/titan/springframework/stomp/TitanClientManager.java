package org.traffichunter.titan.springframework.stomp;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.traffichunter.titan.core.resilience.retry.RetryExecutor;
import org.traffichunter.titan.core.resilience.retry.RetryExecutors;
import org.traffichunter.titan.core.resilience.retry.RetryListener;
import org.traffichunter.titan.core.transport.stomp.client.StompClient;
import org.traffichunter.titan.core.transport.stomp.client.StompOperations;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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
    private final RetryExecutor retryExecutor;

    private final AtomicReference<Status> status = new AtomicReference<>(Status.INITIALIZED);

    public TitanClientManager(StompClient stompClient, TitanProperties properties) {
        this.stompClient = stompClient;
        this.properties = properties;

        TitanProperties.Retry retry = properties.getRetry();
        if (retry.isEnabled()) {
            this.retryExecutor = RetryExecutors.eventLoopRetryExecutor(retry.toPolicy(), new LoggingRetryListener());
        } else {
            this.retryExecutor = RetryExecutors.noopRetryExecutor();
        }
    }

    private enum Status {
        INITIALIZED,
        STARTING,
        STARTED,
        CONNECTING,
        CONNECTED,
        RECONNECTING,
        SHUTTING_DOWN,
        SHUTDOWN,
        ;

        static boolean isRunning(Status status) {
            return switch (status) {
                case STARTING, STARTED, CONNECTING, CONNECTED, RECONNECTING -> true;
                case INITIALIZED, SHUTTING_DOWN, SHUTDOWN -> false;
            };
        }
    }

    @Override
    public void start() {
        if (!status.compareAndSet(Status.INITIALIZED, Status.STARTING)) {
            return;
        }

        try {
            if (!stompClient.isStarted()) {
                stompClient.start();
            }
            status.set(Status.STARTED);
            if (properties.isAutoConnect()) {
                try {
                    connect();
                } catch (Exception e) {
                    if (!properties.getRetry().isEnabled()) {
                        log.error("Disable retry", e);
                        throw e;
                    }

                    log.warn("Failed to connect Titan STOMP client. Scheduling retry.", e);
                    retryExecutor.retry(this::connect);
                }
            }
            log.info("Started Titan Client Manager");
        } catch (Exception e) {
            status.set(Status.INITIALIZED);
            throw new IllegalStateException("Failed to start Titan STOMP client manager", e);
        }
    }

    @Override
    public void stop() {
        if (!transitionToShuttingDown()) {
            return;
        }

        try {
            stompClient.shutdown(DEFAULT_TIMEOUT, TimeUnit.SECONDS);
            retryExecutor.shutdown(DEFAULT_TIMEOUT, TimeUnit.SECONDS);
            log.info("Shutting down Titan STOMP client manager");
        } catch (Exception e) {
            log.warn("Failed to shutdown STOMP client cleanly", e);
        } finally {
            status.set(Status.SHUTDOWN);
        }
    }

    @Override
    public void stop(Runnable callback) {
        stop();
        callback.run();
    }

    @Override
    public boolean isRunning() {
        return Status.isRunning(status.get());
    }

    public boolean isShuttingDown() {
        return status.get() == Status.SHUTTING_DOWN;
    }

    public boolean isShutdown() {
        return status.get() == Status.SHUTDOWN;
    }

    @Override
    public boolean isAutoStartup() {
        return properties.isAutoStart();
    }

    @Override
    public int getPhase() {
        return PHASE;
    }

    public StompOperations operations() throws Exception {
        if (isConnected()) {
            return stompClient.operations();
        }

        return connect();
    }

    public long connectTimeoutMillis() {
        return properties.getConnectTimeoutMillis();
    }

    public @Nullable StompOperations currentOperations() {
        try {
            return stompClient.operations();
        } catch (IllegalStateException e) {
            return null;
        }
    }

    public boolean isReconnecting() {
        return status.get() == Status.RECONNECTING;
    }

    public boolean isConnected() {
        try {
            return stompClient.operations().isConnected();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    private StompOperations connect() throws Exception {
        if (isShutdown()) {
            throw new IllegalStateException("Titan STOMP client manager has been shut down");
        }

        Status previous = status.get();
        if (previous == Status.SHUTTING_DOWN || previous == Status.SHUTDOWN) {
            throw new IllegalStateException("Titan STOMP client manager is shutting down");
        }

        if (!isReconnecting()) {
            status.set(Status.CONNECTING);
        }

        if (!stompClient.isStarted()) {
            stompClient.start();
        }

        try {
            StompOperations operations = stompClient.connect()
                    .get(properties.getConnectTimeoutMillis(), TimeUnit.MILLISECONDS);

            registerReconnectOperation(operations);
            status.set(Status.CONNECTED);
            return operations;
        } catch (Exception e) {
            restoreAfterConnectFailure(previous);
            throw e;
        }
    }

    private void registerReconnectOperation(StompOperations operations) {
        if (isShutdown()) {
            throw new IllegalStateException("Titan STOMP client manager has been shut down");
        }

        operations.connectionDroppedHandler(oper -> reconnect());
        operations.exceptionHandler(throwable -> reconnect());
    }

    private void reconnect() {
        if (!properties.getRetry().isEnabled() || !properties.getReconnect().isEnabled()) {
            return;
        }
        if (!transitionToReconnecting()) {
            return;
        }

        retryExecutor.retry(this::connect);
    }

    private boolean transitionToShuttingDown() {
        while (true) {
            Status current = status.get();
            if (!Status.isRunning(current)) {
                return false;
            }
            if (status.compareAndSet(current, Status.SHUTTING_DOWN)) {
                return true;
            }
        }
    }

    private boolean transitionToReconnecting() {
        return status.compareAndSet(Status.CONNECTED, Status.RECONNECTING);
    }

    private void restoreAfterConnectFailure(Status previous) {
        if (status.get() == Status.SHUTTING_DOWN || status.get() == Status.SHUTDOWN) {
            return;
        }
        status.set(previous == Status.STARTING ? Status.STARTED : previous);
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
