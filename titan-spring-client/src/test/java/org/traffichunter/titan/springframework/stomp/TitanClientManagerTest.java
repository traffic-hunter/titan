package org.traffichunter.titan.springframework.stomp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.traffichunter.titan.core.resilience.retry.CompositeRetryListener;
import org.traffichunter.titan.core.resilience.retry.RetryListener;
import org.traffichunter.titan.core.transport.stomp.client.StompClient;
import org.traffichunter.titan.core.transport.stomp.client.StompOperations;
import org.traffichunter.titan.core.util.Handler;

class TitanClientManagerTest {

    private StompClient client;
    private StompOperations operations;
    private TitanProperties properties;
    private TitanClientManager manager;

    @BeforeEach
    void setUp() {
        client = mock(StompClient.class);
        operations = mock(StompOperations.class);
        properties = new TitanProperties();

        when(client.connect()).thenReturn(CompletableFuture.completedFuture(operations));
        when(client.operations()).thenReturn(operations);
    }

    @AfterEach
    void tearDown() {
        if (manager != null) {
            manager.stop();
        }
    }

    @Test
    void start_starts_client_and_connects_when_auto_connect_enabled() throws Exception {
        when(client.isStarted()).thenReturn(false, true);
        manager = new TitanClientManager(client, properties);

        manager.start();

        verify(client).start();
        verify(client).connect();
        assertThat(manager.isRunning()).isTrue();
    }

    @Test
    void start_only_starts_client_when_auto_connect_disabled() {
        properties.setAutoConnect(false);
        manager = new TitanClientManager(client, properties);

        manager.start();

        verify(client).start();
        verify(client, never()).connect();
        assertThat(manager.isRunning()).isTrue();
    }

    @Test
    void start_is_idempotent_while_manager_is_running() {
        when(client.isStarted()).thenReturn(false, true, true);
        manager = new TitanClientManager(client, properties);

        manager.start();
        manager.start();

        verify(client, times(1)).start();
        verify(client, times(1)).connect();
        assertThat(manager.isRunning()).isTrue();
    }

    @Test
    void start_failure_restores_initialized_state() {
        when(client.connect())
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("connect failed")))
                .thenReturn(CompletableFuture.completedFuture(operations));
        manager = new TitanClientManager(client, properties);

        assertThatThrownBy(manager::start)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Failed to start Titan STOMP client manager");
        assertThat(manager.isRunning()).isFalse();

        manager.start();

        verify(client, times(2)).connect();
        assertThat(manager.isRunning()).isTrue();
    }

    @Test
    void start_connects_immediately_and_retries_when_retry_is_enabled() {
        properties.getRetry().setEnabled(true);
        properties.getRetry().setType(TitanProperties.Retry.Type.FIX);
        properties.getRetry().setMaxAttempts(2);
        properties.getRetry().setDelay(Duration.ofMillis(10));
        when(client.connect())
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("connect failed")))
                .thenReturn(CompletableFuture.completedFuture(operations));
        manager = new TitanClientManager(client, properties);

        manager.start();

        verify(client).connect();
        verify(client, timeout(1000).times(2)).connect();
    }

    @Test
    void retry_listener_can_be_added_after_manager_creation() {
        enableFastRetry();
        CompositeRetryListener listeners = new CompositeRetryListener();
        CompletableFuture<Integer> retryAttempt = new CompletableFuture<>();
        manager = new TitanClientManager(client, properties, listeners);
        listeners.add(new RetryListener() {
            @Override
            public void onRetry(int attempt, Duration delay) {
                retryAttempt.complete(attempt);
            }
        });
        when(client.connect())
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("connect failed")))
                .thenReturn(CompletableFuture.completedFuture(operations));

        manager.start();

        assertThat(retryAttempt).succeedsWithin(Duration.ofSeconds(1))
                .isEqualTo(1);
    }

    @Test
    void operations_start_and_connect_client_when_no_connection_exists() throws Exception {
        when(client.operations())
                .thenThrow(new IllegalStateException("not connected"))
                .thenReturn(operations);
        manager = new TitanClientManager(client, properties);

        StompOperations resolved = manager.operations();

        assertThat(resolved).isSameAs(operations);
        verify(client).start();
        verify(client).connect();
    }

    @Test
    void operations_reuses_current_connection_when_connected() throws Exception {
        when(operations.isConnected()).thenReturn(true);
        manager = new TitanClientManager(client, properties);

        StompOperations resolved = manager.operations();

        assertThat(resolved).isSameAs(operations);
        verify(client, never()).connect();
    }

    @Test
    void current_operations_returns_null_when_no_connection_exists() {
        when(client.operations()).thenThrow(new IllegalStateException("not connected"));
        manager = new TitanClientManager(client, properties);

        assertThat(manager.currentOperations()).isNull();
    }

    @Test
    void stop_shuts_down_client_with_configured_timeout_window() {
        manager = new TitanClientManager(client, properties);
        manager.start();

        manager.stop();

        verify(client).shutdown(30, TimeUnit.SECONDS);
        assertThat(manager.isRunning()).isFalse();
    }

    @Test
    void connect_registers_reconnect_handlers() {
        manager = new TitanClientManager(client, properties);

        manager.start();

        assertThat(captureConnectionDroppedHandler()).isNotNull();
        assertThat(captureExceptionHandler()).isNotNull();
    }

    @Test
    void connection_dropped_reconnects_when_retry_and_reconnect_are_enabled() {
        enableFastRetry();
        manager = new TitanClientManager(client, properties);
        manager.start();

        captureConnectionDroppedHandler().handle(operations);

        verify(client, timeout(1000).times(2)).connect();
        await().atMost(Duration.ofSeconds(1))
                .untilAsserted(() -> assertThat(manager.isReconnecting()).isFalse());
    }

    @Test
    void exception_reconnects_when_retry_and_reconnect_are_enabled() {
        enableFastRetry();
        manager = new TitanClientManager(client, properties);
        manager.start();

        captureExceptionHandler().handle(new IllegalStateException("connection lost"));

        verify(client, timeout(1000).times(2)).connect();
        await().atMost(Duration.ofSeconds(1))
                .untilAsserted(() -> assertThat(manager.isReconnecting()).isFalse());
    }

    @Test
    void duplicate_connection_dropped_events_schedule_one_reconnect() {
        enableFastRetry();
        CompletableFuture<StompOperations> reconnect = new CompletableFuture<>();
        when(client.connect())
                .thenReturn(CompletableFuture.completedFuture(operations))
                .thenReturn(reconnect);
        manager = new TitanClientManager(client, properties);
        manager.start();
        Handler<StompOperations> droppedHandler = captureConnectionDroppedHandler();

        droppedHandler.handle(operations);
        droppedHandler.handle(operations);

        verify(client, timeout(1000).times(2)).connect();
        reconnect.complete(operations);
        await().atMost(Duration.ofSeconds(1))
                .untilAsserted(() -> assertThat(manager.isReconnecting()).isFalse());
        verify(client, times(2)).connect();
    }

    @Test
    void successful_reconnect_registers_handlers_on_the_new_connection() {
        enableFastRetry();
        when(client.connect())
                .thenReturn(CompletableFuture.completedFuture(operations))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("connection unavailable")))
                .thenReturn(CompletableFuture.completedFuture(operations));
        manager = new TitanClientManager(client, properties);
        manager.start();

        captureConnectionDroppedHandler().handle(operations);

        verify(client, timeout(1000).times(3)).connect();
        verify(operations, timeout(1000).times(2)).connectionDroppedHandler(org.mockito.ArgumentMatchers.any());
        verify(operations, timeout(1000).times(2)).exceptionHandler(org.mockito.ArgumentMatchers.any());
        assertThat(manager.isReconnecting()).isFalse();
    }

    @Test
    void exhausted_reconnect_transitions_to_disconnected() {
        enableFastRetry();
        when(client.connect())
                .thenReturn(CompletableFuture.completedFuture(operations))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("connection unavailable")));
        manager = reconnectAwareManager();
        manager.start();

        captureConnectionDroppedHandler().handle(operations);

        verify(client, timeout(1000).times(3)).connect();
        await().atMost(Duration.ofSeconds(1))
                .untilAsserted(() -> assertThat(manager.isReconnecting()).isFalse());
        assertThat(manager.isRunning()).isTrue();
        assertThat(manager.isDisconnected()).isTrue();
    }

    @Test
    void operations_can_reconnect_after_retry_exhaustion() throws Exception {
        enableFastRetry();
        when(client.connect())
                .thenReturn(CompletableFuture.completedFuture(operations))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("connection unavailable")))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("connection unavailable")))
                .thenReturn(CompletableFuture.completedFuture(operations));
        when(operations.isConnected()).thenReturn(false, true);
        manager = reconnectAwareManager();
        manager.start();

        captureConnectionDroppedHandler().handle(operations);

        verify(client, timeout(1000).times(3)).connect();
        await().atMost(Duration.ofSeconds(1))
                .untilAsserted(() -> assertThat(manager.isDisconnected()).isTrue());

        assertThat(manager.operations()).isSameAs(operations);
        assertThat(manager.isConnected()).isTrue();
        assertThat(manager.isDisconnected()).isFalse();
    }

    @Test
    void stop_prevents_a_scheduled_reconnect_attempt() {
        properties.getRetry().setEnabled(true);
        properties.getRetry().setType(TitanProperties.Retry.Type.FIX);
        properties.getRetry().setMaxAttempts(2);
        properties.getRetry().setDelay(Duration.ofMillis(300));
        manager = new TitanClientManager(client, properties);
        manager.start();

        captureConnectionDroppedHandler().handle(operations);
        manager.stop();

        await().during(Duration.ofMillis(400))
                .atMost(Duration.ofSeconds(1))
                .untilAsserted(() -> verify(client, times(1)).connect());
        assertThat(manager.isShutdown()).isTrue();
    }

    @Test
    void connection_dropped_does_not_reconnect_when_retry_is_disabled() {
        manager = new TitanClientManager(client, properties);
        manager.start();

        captureConnectionDroppedHandler().handle(operations);

        verify(client, times(1)).connect();
        assertThat(manager.isReconnecting()).isFalse();
    }

    @Test
    void connection_dropped_does_not_reconnect_when_reconnect_is_disabled() {
        enableFastRetry();
        properties.getReconnect().setEnabled(false);
        manager = new TitanClientManager(client, properties);
        manager.start();

        captureConnectionDroppedHandler().handle(operations);

        verify(client, times(1)).connect();
        assertThat(manager.isReconnecting()).isFalse();
    }

    private void enableFastRetry() {
        properties.getRetry().setEnabled(true);
        properties.getRetry().setType(TitanProperties.Retry.Type.FIX);
        properties.getRetry().setMaxAttempts(2);
        properties.getRetry().setDelay(Duration.ofMillis(10));
    }

    private TitanClientManager reconnectAwareManager() {
        CompositeRetryListener listeners = new CompositeRetryListener();
        TitanClientManager clientManager = new TitanClientManager(client, properties, listeners);
        listeners.add(new TitanReconnectStateRetryListener(() -> clientManager));
        return clientManager;
    }

    private Handler<StompOperations> captureConnectionDroppedHandler() {
        ArgumentCaptor<Handler<StompOperations>> captor = captureOperationsHandler();
        verify(operations).connectionDroppedHandler(captor.capture());
        return captor.getValue();
    }

    private Handler<Throwable> captureExceptionHandler() {
        ArgumentCaptor<Handler<Throwable>> captor = captureThrowableHandler();
        verify(operations).exceptionHandler(captor.capture());
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<Handler<StompOperations>> captureOperationsHandler() {
        return ArgumentCaptor.forClass(Handler.class);
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<Handler<Throwable>> captureThrowableHandler() {
        return ArgumentCaptor.forClass(Handler.class);
    }
}
