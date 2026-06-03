package org.traffichunter.titan.core.transport.stomp.client;

import io.vertx.ext.stomp.Command;
import io.vertx.ext.stomp.Frame;
import io.vertx.ext.stomp.StompClientConnection;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.traffichunter.titan.core.codec.stomp.StompCommand;
import org.traffichunter.titan.core.codec.stomp.StompFrames;
import org.traffichunter.titan.core.codec.stomp.StompHeaders.Elements;
import org.traffichunter.titan.core.util.buffer.Buffer;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VertxStompOperationsTest {

    @Test
    void sends_with_converted_headers_and_payload() throws Exception {
        StompClientConnection connection = mock(StompClientConnection.class);
        Frame receipt = new Frame().setCommand(Command.RECEIPT);
        when(connection.send(eq("/topic/test"), anyMap(), any(io.vertx.core.buffer.Buffer.class)))
                .thenReturn(io.vertx.core.Future.succeededFuture(receipt));

        VertxStompOperations operations = new VertxStompOperations(connection);

        StompFrames result = operations.send(
                "/topic/test",
                Buffer.alloc("hello"),
                Map.of(
                        Elements.ID, "sub-1",
                        Elements.RECEIPT, "receipt-1"
                )
        ).get();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> headers = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<io.vertx.core.buffer.Buffer> payload = ArgumentCaptor.forClass(io.vertx.core.buffer.Buffer.class);
        verify(connection).send(eq("/topic/test"), headers.capture(), payload.capture());

        assertThat(result.command()).isEqualTo(StompCommand.RECEIPT);
        assertThat(headers.getValue())
                .containsEntry("id", "sub-1")
                .containsEntry("receipt", "receipt-1");
        assertThat(payload.getValue().toString(StandardCharsets.UTF_8)).isEqualTo("hello");
    }

    @Test
    void subscribes_with_converted_headers_and_wrapped_message_handler() throws Exception {
        StompClientConnection connection = mock(StompClientConnection.class);
        when(connection.subscribe(
                eq("/topic/test"),
                anyMap(),
                any()
        )).thenReturn(io.vertx.core.Future.succeededFuture("sub-1"));

        VertxStompOperations operations = new VertxStompOperations(connection);
        AtomicReference<StompFrames> received = new AtomicReference<>();

        String subscriptionId = operations.subscribe(
                "/topic/test",
                Map.of(Elements.ID, "sub-1"),
                received::set
        ).get();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> headers = ArgumentCaptor.forClass(Map.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<io.vertx.core.Handler<Frame>> handler = ArgumentCaptor.forClass(io.vertx.core.Handler.class);
        verify(connection).subscribe(eq("/topic/test"), headers.capture(), handler.capture());

        Frame message = new Frame(
                Command.MESSAGE,
                Map.of(Frame.DESTINATION, "/topic/test"),
                io.vertx.core.buffer.Buffer.buffer("body")
        );
        handler.getValue().handle(message);

        assertThat(subscriptionId).isEqualTo("sub-1");
        assertThat(headers.getValue()).containsEntry("id", "sub-1");
        assertThat(received.get()).isNotNull();
        assertThat(received.get().command()).isEqualTo(StompCommand.MESSAGE);
        assertThat(new String(received.get().body(), StandardCharsets.UTF_8)).isEqualTo("body");
    }

    @Test
    void delegates_connection_state() {
        StompClientConnection connection = mock(StompClientConnection.class);
        when(connection.isConnected()).thenReturn(true);

        VertxStompOperations operations = new VertxStompOperations(connection);

        assertThat(operations.isConnected()).isTrue();
    }

    @Test
    void delegates_lifecycle_handlers() {
        StompClientConnection connection = mock(StompClientConnection.class);
        VertxStompOperations operations = new VertxStompOperations(connection);
        AtomicBoolean closed = new AtomicBoolean(false);
        AtomicBoolean dropped = new AtomicBoolean(false);
        AtomicBoolean ping = new AtomicBoolean(false);
        AtomicReference<Throwable> exception = new AtomicReference<>();

        operations.closeHandler(value -> closed.set(value == operations));
        operations.connectionDroppedHandler(value -> dropped.set(value == operations));
        operations.pingHandler(value -> ping.set(value == operations));
        operations.exceptionHandler(exception::set);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<io.vertx.core.Handler<StompClientConnection>> closeHandler = ArgumentCaptor.forClass(io.vertx.core.Handler.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<io.vertx.core.Handler<StompClientConnection>> droppedHandler = ArgumentCaptor.forClass(io.vertx.core.Handler.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<io.vertx.core.Handler<StompClientConnection>> pingHandler = ArgumentCaptor.forClass(io.vertx.core.Handler.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<io.vertx.core.Handler<Throwable>> exceptionHandler = ArgumentCaptor.forClass(io.vertx.core.Handler.class);

        verify(connection).closeHandler(closeHandler.capture());
        verify(connection).connectionDroppedHandler(droppedHandler.capture());
        verify(connection).pingHandler(pingHandler.capture());
        verify(connection).exceptionHandler(exceptionHandler.capture());

        RuntimeException error = new RuntimeException("failed");
        closeHandler.getValue().handle(connection);
        droppedHandler.getValue().handle(connection);
        pingHandler.getValue().handle(connection);
        exceptionHandler.getValue().handle(error);

        assertThat(closed).isTrue();
        assertThat(dropped).isTrue();
        assertThat(ping).isTrue();
        assertThat(exception).hasValue(error);
    }
}
