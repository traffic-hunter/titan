package org.traffichunter.titan.springframework.stomp.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.traffichunter.titan.client.TitanClient;
import org.traffichunter.titan.springframework.stomp.TitanProperties;

class TitanClientManagerTest {

    private TitanClient client;
    private TitanProperties properties;
    private TitanClientManager manager;

    @BeforeEach
    void setUp() {
        client = mock(TitanClient.class);
        properties = new TitanProperties();

        when(client.connect()).thenReturn(CompletableFuture.completedFuture(client));

        // Mirror a real client: start()/shutdown() flip the client's own lifecycle flags,
        // which the manager now treats as the single source of truth.
        doAnswer(invocation -> {
            when(client.isStarted()).thenReturn(true);
            return null;
        }).when(client).start();
        doAnswer(invocation -> {
            when(client.isShutdown()).thenReturn(true);
            return null;
        }).when(client).shutdown(anyLong(), any());
    }

    @AfterEach
    void tearDown() {
        if (manager != null) {
            manager.stop();
        }
    }

    @Test
    void start_starts_client_and_connects_when_auto_connect_enabled() {
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
    void start_is_idempotent_while_client_is_started() {
        manager = new TitanClientManager(client, properties);

        manager.start();
        manager.start();

        verify(client, times(1)).start();
        verify(client, times(1)).connect();
        assertThat(manager.isRunning()).isTrue();
    }

    @Test
    void start_propagates_connect_failure() {
        when(client.connect())
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("connect failed")));
        manager = new TitanClientManager(client, properties);

        assertThatThrownBy(manager::start)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Failed to start Titan STOMP client manager");
    }

    @Test
    void connection_recovers_after_initial_connect_failure() throws Exception {
        when(client.connect())
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("connect failed")))
                .thenReturn(CompletableFuture.completedFuture(client));
        manager = new TitanClientManager(client, properties);

        assertThatThrownBy(manager::start).isInstanceOf(IllegalStateException.class);

        TitanClient resolved = manager.connection();

        assertThat(resolved).isSameAs(client);
        verify(client, times(1)).start();
        verify(client, times(2)).connect();
    }

    @Test
    void connection_starts_and_connects_client_when_no_connection_exists() throws Exception {
        manager = new TitanClientManager(client, properties);

        TitanClient resolved = manager.connection();

        assertThat(resolved).isSameAs(client);
        verify(client).start();
        verify(client).connect();
    }

    @Test
    void connection_reuses_current_connection_when_connected() throws Exception {
        when(client.isConnected()).thenReturn(true);
        manager = new TitanClientManager(client, properties);

        TitanClient resolved = manager.connection();

        assertThat(resolved).isSameAs(client);
        verify(client, never()).connect();
    }

    @Test
    void current_connection_returns_null_when_no_connection_exists() {
        when(client.isConnected()).thenReturn(false);
        manager = new TitanClientManager(client, properties);

        assertThat(manager.currentConnection()).isNull();
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
    void stop_is_idempotent_and_no_op_before_start() {
        manager = new TitanClientManager(client, properties);

        manager.stop();
        manager.start();
        manager.stop();
        manager.stop();

        verify(client, times(1)).shutdown(anyLong(), any());
        assertThat(manager.isRunning()).isFalse();
    }
}
