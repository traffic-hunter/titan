package org.traffichunter.titan.core.transport.stomp;

import io.vertx.core.Vertx;
import io.vertx.ext.stomp.StompClientConnection;
import org.junit.jupiter.api.Test;
import org.traffichunter.titan.core.codec.stomp.StompException;
import org.traffichunter.titan.core.transport.stomp.client.StompOperations;
import org.traffichunter.titan.core.transport.stomp.client.VertxStompOperations;
import org.traffichunter.titan.core.transport.stomp.option.StompClientOption;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VertxStompClientTest {

    @Test
    void rejects_connect_before_start() {
        VertxStompClient client = VertxStompClient.open(StompClientOption.builder().build());

        assertThatThrownBy(() -> client.connect().get())
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(StompException.class)
                .hasRootCauseMessage("Client is not started");
    }

    @Test
    void connects_with_wrapped_native_client_and_caches_operations() throws Exception {
        io.vertx.ext.stomp.StompClient nativeClient = mock(io.vertx.ext.stomp.StompClient.class);
        StompClientConnection connection = mock(StompClientConnection.class);
        StompClientOption option = StompClientOption.builder()
                .host("127.0.0.1")
                .port(61613)
                .build();

        when(nativeClient.vertx()).thenReturn(mock(Vertx.class));
        when(nativeClient.isClosed()).thenReturn(false);
        when(nativeClient.connect(61613, "127.0.0.1"))
                .thenReturn(io.vertx.core.Future.succeededFuture(connection));

        VertxStompClient client = VertxStompClient.wrap(nativeClient, option);

        StompOperations operations = client.connect().get();

        assertThat(operations).isInstanceOf(VertxStompOperations.class);
        assertThat(client.operations()).isSameAs(operations);
        assertThat(client.connection()).isSameAs(connection);
    }

    @Test
    void rejects_connect_when_already_connected() throws Exception {
        io.vertx.ext.stomp.StompClient nativeClient = mock(io.vertx.ext.stomp.StompClient.class);
        StompClientConnection connection = mock(StompClientConnection.class);
        StompClientOption option = StompClientOption.builder().build();

        when(nativeClient.vertx()).thenReturn(mock(Vertx.class));
        when(nativeClient.isClosed()).thenReturn(false);
        when(nativeClient.connect(option.port(), option.host()))
                .thenReturn(io.vertx.core.Future.succeededFuture(connection));
        when(connection.isConnected()).thenReturn(true);

        VertxStompClient client = VertxStompClient.wrap(nativeClient, option);
        client.connect().get();

        assertThatThrownBy(() -> client.connect().get())
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(StompException.class);
    }

    @Test
    void shutdown_closes_native_client() {
        io.vertx.ext.stomp.StompClient nativeClient = mock(io.vertx.ext.stomp.StompClient.class);
        StompClientOption option = StompClientOption.builder().build();

        when(nativeClient.vertx()).thenReturn(mock(Vertx.class));
        when(nativeClient.isClosed()).thenReturn(false);
        when(nativeClient.close()).thenReturn(io.vertx.core.Future.succeededFuture());

        VertxStompClient client = VertxStompClient.wrap(nativeClient, option);

        client.shutdown(1, TimeUnit.SECONDS);

        verify(nativeClient).close();
        assertThat(client.isShutdown()).isTrue();
    }
}
