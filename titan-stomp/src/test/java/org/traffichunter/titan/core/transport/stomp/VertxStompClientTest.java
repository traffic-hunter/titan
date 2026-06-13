package org.traffichunter.titan.core.transport.stomp;

import io.vertx.core.Vertx;
import io.vertx.ext.stomp.StompClientConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.traffichunter.titan.core.codec.stomp.StompException;
import org.traffichunter.titan.core.resilience.retry.RetryPolicy;
import org.traffichunter.titan.core.transport.stomp.client.StompConnection;
import org.traffichunter.titan.core.transport.stomp.client.VertxStompConnection;
import org.traffichunter.titan.core.transport.stomp.option.StompClientOption;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VertxStompClientTest {

    private VertxStompClient client;
    private Vertx vertx;

    @AfterEach
    void tearDown() throws Exception {
        if (client != null && !client.isShutdown()) {
            client.shutdown(1, TimeUnit.SECONDS);
        }
        if (vertx != null) {
            vertx.close().await(1, TimeUnit.SECONDS);
        }
    }

    @Test
    void rejects_connect_before_start() {
        client = VertxStompClient.open(StompClientOption.builder().build());

        assertThatThrownBy(() -> client.connect().get())
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(StompException.class)
                .hasRootCauseMessage("Client is not started");
    }

    @Test
    void connects_with_wrapped_native_client_and_caches_connection() throws Exception {
        io.vertx.ext.stomp.StompClient nativeClient = mock(io.vertx.ext.stomp.StompClient.class);
        StompClientConnection nativeConnection = mock(StompClientConnection.class);
        StompClientOption option = StompClientOption.builder()
                .host("127.0.0.1")
                .port(61613)
                .build();

        vertx = Vertx.vertx();
        when(nativeClient.vertx()).thenReturn(vertx);
        when(nativeClient.isClosed()).thenReturn(false);
        when(nativeClient.close()).thenReturn(io.vertx.core.Future.succeededFuture());
        when(nativeClient.connect(61613, "127.0.0.1"))
                .thenReturn(io.vertx.core.Future.succeededFuture(nativeConnection));

        client = VertxStompClient.wrap(nativeClient, option);

        StompConnection stompConnection = client.connect().get();

        assertThat(stompConnection).isInstanceOf(VertxStompConnection.class);
        assertThat(client.channel()).isSameAs(nativeConnection);
        assertThat(client.connection()).isSameAs(stompConnection);
    }

    @Test
    void rejects_connect_when_already_connected() throws Exception {
        io.vertx.ext.stomp.StompClient nativeClient = mock(io.vertx.ext.stomp.StompClient.class);
        StompClientConnection connection = mock(StompClientConnection.class);
        StompClientOption option = StompClientOption.builder().build();

        vertx = Vertx.vertx();
        when(nativeClient.vertx()).thenReturn(vertx);
        when(nativeClient.isClosed()).thenReturn(false);
        when(nativeClient.close()).thenReturn(io.vertx.core.Future.succeededFuture());
        when(nativeClient.connect(option.port(), option.host()))
                .thenReturn(io.vertx.core.Future.succeededFuture(connection));
        when(connection.isConnected()).thenReturn(true);

        client = VertxStompClient.wrap(nativeClient, option);
        client.connect().get();

        assertThatThrownBy(() -> client.connect().get())
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(StompException.class);
    }

    @Test
    void shutdown_closes_native_client() {
        io.vertx.ext.stomp.StompClient nativeClient = mock(io.vertx.ext.stomp.StompClient.class);
        StompClientOption option = StompClientOption.builder().build();

        vertx = Vertx.vertx();
        when(nativeClient.vertx()).thenReturn(vertx);
        when(nativeClient.isClosed()).thenReturn(false);
        when(nativeClient.close()).thenReturn(io.vertx.core.Future.succeededFuture());

        client = VertxStompClient.wrap(nativeClient, option);

        client.shutdown(1, TimeUnit.SECONDS);

        verify(nativeClient).close();
        assertThat(client.isShutdown()).isTrue();
    }

    @Test
    void reconnects_until_connection_is_restored() throws Exception {
        io.vertx.ext.stomp.StompClient nativeClient = mock(io.vertx.ext.stomp.StompClient.class);
        StompClientConnection firstConnection = mock(StompClientConnection.class);
        StompClientConnection restoredConnection = mock(StompClientConnection.class);
        StompClientOption option = fastReconnectOption();

        vertx = Vertx.vertx();
        when(nativeClient.vertx()).thenReturn(vertx);
        when(nativeClient.isClosed()).thenReturn(false);
        when(nativeClient.close()).thenReturn(io.vertx.core.Future.succeededFuture());
        when(nativeClient.connect(option.port(), option.host()))
                .thenReturn(io.vertx.core.Future.succeededFuture(firstConnection))
                .thenReturn(io.vertx.core.Future.failedFuture("unavailable"))
                .thenReturn(io.vertx.core.Future.succeededFuture(restoredConnection));

        client = VertxStompClient.wrap(nativeClient, option);
        client.connect().get();

        connectionDroppedHandler(firstConnection).handle(firstConnection);

        verify(nativeClient, timeout(1000).times(3)).connect(option.port(), option.host());
        assertThat(client.channel()).isSameAs(restoredConnection);
        assertThat(client.connection()).isInstanceOf(VertxStompConnection.class);
    }

    @Test
    void duplicate_connection_loss_events_start_one_reconnect_sequence() throws Exception {
        io.vertx.ext.stomp.StompClient nativeClient = mock(io.vertx.ext.stomp.StompClient.class);
        StompClientConnection nativeConnection = mock(StompClientConnection.class);
        StompClientConnection restoredConnection = mock(StompClientConnection.class);
        StompClientOption option = fastReconnectOption();

        vertx = Vertx.vertx();
        when(nativeClient.vertx()).thenReturn(vertx);
        when(nativeClient.isClosed()).thenReturn(false);
        when(nativeClient.close()).thenReturn(io.vertx.core.Future.succeededFuture());
        when(nativeClient.connect(option.port(), option.host()))
                .thenReturn(io.vertx.core.Future.succeededFuture(nativeConnection))
                .thenReturn(io.vertx.core.Future.succeededFuture(restoredConnection));

        client = VertxStompClient.wrap(nativeClient, option);
        client.connect().get();

        io.vertx.core.Handler<StompClientConnection> dropped = connectionDroppedHandler(nativeConnection);
        io.vertx.core.Handler<Throwable> exception = exceptionHandler(nativeConnection);
        dropped.handle(nativeConnection);
        exception.handle(new IllegalStateException("connection lost"));

        verify(nativeClient, timeout(1000).times(2)).connect(option.port(), option.host());
        verify(nativeClient, after(100).times(2)).connect(option.port(), option.host());
    }

    @Test
    void explicit_disconnect_does_not_reconnect() throws Exception {
        io.vertx.ext.stomp.StompClient nativeClient = mock(io.vertx.ext.stomp.StompClient.class);
        StompClientConnection nativeConnection = mock(StompClientConnection.class);
        StompClientOption option = fastReconnectOption();

        when(nativeClient.vertx()).thenReturn(mock(Vertx.class));
        when(nativeClient.isClosed()).thenReturn(false);
        when(nativeClient.close()).thenReturn(io.vertx.core.Future.succeededFuture());
        when(nativeClient.connect(option.port(), option.host()))
                .thenReturn(io.vertx.core.Future.succeededFuture(nativeConnection));
        when(nativeConnection.disconnect()).thenReturn(io.vertx.core.Future.succeededFuture());

        client = VertxStompClient.wrap(nativeClient, option);
        StompConnection stompConnection = client.connect().get();
        stompConnection.disconnect().get();

        closeHandler(nativeConnection).handle(nativeConnection);

        verify(nativeClient, after(100).times(1)).connect(option.port(), option.host());
    }

    @Test
    void default_reconnect_policy_has_unlimited_attempts() {
        StompClientOption option = StompClientOption.builder().build();

        assertThat(option.reconnectPolicy().maxAttempts())
                .isEqualTo(RetryPolicy.UNLIMITED_ATTEMPTS);
    }

    private static StompClientOption fastReconnectOption() {
        return StompClientOption.builder()
                .reconnectPolicy(RetryPolicy.fixed(
                        RetryPolicy.UNLIMITED_ATTEMPTS,
                        Duration.ofMillis(10)
                ))
                .build();
    }

    @SuppressWarnings("unchecked")
    private static io.vertx.core.Handler<StompClientConnection> connectionDroppedHandler(
            StompClientConnection connection
    ) {
        ArgumentCaptor<io.vertx.core.Handler<StompClientConnection>> captor =
                ArgumentCaptor.forClass(io.vertx.core.Handler.class);
        verify(connection).connectionDroppedHandler(captor.capture());
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private static io.vertx.core.Handler<StompClientConnection> closeHandler(
            StompClientConnection connection
    ) {
        ArgumentCaptor<io.vertx.core.Handler<StompClientConnection>> captor =
                ArgumentCaptor.forClass(io.vertx.core.Handler.class);
        verify(connection).closeHandler(captor.capture());
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private static io.vertx.core.Handler<Throwable> exceptionHandler(
            StompClientConnection connection
    ) {
        ArgumentCaptor<io.vertx.core.Handler<Throwable>> captor =
                ArgumentCaptor.forClass(io.vertx.core.Handler.class);
        verify(connection).exceptionHandler(captor.capture());
        return captor.getValue();
    }
}
