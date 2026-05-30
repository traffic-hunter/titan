package org.traffichunter.titan.springframework.stomp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.traffichunter.titan.core.transport.stomp.client.StompClient;
import org.traffichunter.titan.core.transport.stomp.client.StompClientOperations;

import static org.mockito.Mockito.mock;

class TitanClientManagerTest {

    private StompClient client;
    private StompClientOperations operations;
    private TitanProperties properties;

    @BeforeEach
    void setUp() {
        client = mock(StompClient.class);
        operations = mock(StompClientOperations.class);
        properties = new TitanProperties();

        when(client.connect()).thenReturn(CompletableFuture.completedFuture(operations));
        when(client.operations()).thenReturn(operations);
    }

    @Test
    void start_starts_client_and_connects_when_auto_connect_enabled() throws Exception {
        when(client.isStarted()).thenReturn(false, true);
        TitanClientManager manager = new TitanClientManager(client, properties);

        manager.start();

        verify(client).start();
        verify(client).connect();
        assertThat(manager.isRunning()).isTrue();
    }

    @Test
    void start_only_starts_client_when_auto_connect_disabled() {
        properties.setAutoConnect(false);
        TitanClientManager manager = new TitanClientManager(client, properties);

        manager.start();

        verify(client).start();
        verify(client, never()).connect();
        assertThat(manager.isRunning()).isTrue();
    }

    @Test
    void operations_start_and_connect_client_when_no_connection_exists() throws Exception {
        when(client.operations())
                .thenThrow(new IllegalStateException("not connected"))
                .thenReturn(operations);
        TitanClientManager manager = new TitanClientManager(client, properties);

        StompClientOperations resolved = manager.operations();

        assertThat(resolved).isSameAs(operations);
        verify(client).start();
        verify(client).connect();
    }

    @Test
    void operations_reuses_current_connection_when_connected() throws Exception {
        when(operations.isConnected()).thenReturn(true);
        TitanClientManager manager = new TitanClientManager(client, properties);

        StompClientOperations resolved = manager.operations();

        assertThat(resolved).isSameAs(operations);
        verify(client, never()).connect();
    }

    @Test
    void current_operations_returns_null_when_no_connection_exists() {
        when(client.operations()).thenThrow(new IllegalStateException("not connected"));
        TitanClientManager manager = new TitanClientManager(client, properties);

        assertThat(manager.currentOperations()).isNull();
    }

    @Test
    void stop_shuts_down_client_with_configured_timeout_window() {
        TitanClientManager manager = new TitanClientManager(client, properties);
        manager.start();

        manager.stop();

        verify(client).shutdown(30, TimeUnit.SECONDS);
        assertThat(manager.isRunning()).isFalse();
    }
}
