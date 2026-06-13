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

class VertxStompChannelTest {

    @Test
    void sends_with_converted_headers_and_payload() throws Exception {
        StompClientConnection nativeConnection = mock(StompClientConnection.class);
        Frame receipt = new Frame().setCommand(Command.RECEIPT);
        when(nativeConnection.send(eq("/topic/test"), anyMap(), any(io.vertx.core.buffer.Buffer.class)))
                .thenReturn(io.vertx.core.Future.succeededFuture(receipt));

        VertxStompConnection stompConnection = new VertxStompConnection(nativeConnection);

        StompFrames result = stompConnection.send(
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
        verify(nativeConnection).send(eq("/topic/test"), headers.capture(), payload.capture());

        assertThat(result.command()).isEqualTo(StompCommand.RECEIPT);
        assertThat(headers.getValue())
                .containsEntry("id", "sub-1")
                .containsEntry("receipt", "receipt-1");
        assertThat(payload.getValue().toString(StandardCharsets.UTF_8)).isEqualTo("hello");
    }

    @Test
    void subscribes_with_converted_headers_and_wrapped_message_handler() throws Exception {
        StompClientConnection nativeConnection = mock(StompClientConnection.class);
        when(nativeConnection.subscribe(
                eq("/topic/test"),
                anyMap(),
                any()
        )).thenReturn(io.vertx.core.Future.succeededFuture("sub-1"));

        VertxStompConnection stompConnection = new VertxStompConnection(nativeConnection);
        AtomicReference<StompFrames> received = new AtomicReference<>();

        String subscriptionId = stompConnection.subscribe(
                "/topic/test",
                Map.of(Elements.ID, "sub-1"),
                received::set
        ).get();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> headers = ArgumentCaptor.forClass(Map.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<io.vertx.core.Handler<Frame>> handler = ArgumentCaptor.forClass(io.vertx.core.Handler.class);
        verify(nativeConnection).subscribe(eq("/topic/test"), headers.capture(), handler.capture());

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
        StompClientConnection nativeConnection = mock(StompClientConnection.class);
        when(nativeConnection.isConnected()).thenReturn(true);

        VertxStompConnection stompConnection = new VertxStompConnection(nativeConnection);

        assertThat(stompConnection.isConnected()).isTrue();
    }

    @Test
    void delegates_lifecycle_handlers() {
        StompClientConnection nativeConnection = mock(StompClientConnection.class);
        AtomicBoolean internalConnectionLost = new AtomicBoolean(false);
        AtomicReference<Throwable> internalException = new AtomicReference<>();
        VertxStompConnection stompConnection = new VertxStompConnection(
                nativeConnection,
                () -> {},
                value -> internalConnectionLost.set(value != null),
                internalException::set
        );
        AtomicBoolean closed = new AtomicBoolean(false);
        AtomicBoolean dropped = new AtomicBoolean(false);
        AtomicBoolean ping = new AtomicBoolean(false);
        AtomicReference<Throwable> exception = new AtomicReference<>();

        stompConnection.closeHandler(value -> closed.set(value == stompConnection));
        stompConnection.connectionDroppedHandler(value -> dropped.set(value == stompConnection));
        stompConnection.pingHandler(value -> ping.set(value == stompConnection));
        stompConnection.exceptionHandler(exception::set);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<io.vertx.core.Handler<StompClientConnection>> closeHandler = ArgumentCaptor.forClass(io.vertx.core.Handler.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<io.vertx.core.Handler<StompClientConnection>> droppedHandler = ArgumentCaptor.forClass(io.vertx.core.Handler.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<io.vertx.core.Handler<StompClientConnection>> pingHandler = ArgumentCaptor.forClass(io.vertx.core.Handler.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<io.vertx.core.Handler<Throwable>> exceptionHandler = ArgumentCaptor.forClass(io.vertx.core.Handler.class);

        verify(nativeConnection).closeHandler(closeHandler.capture());
        verify(nativeConnection).connectionDroppedHandler(droppedHandler.capture());
        verify(nativeConnection).pingHandler(pingHandler.capture());
        verify(nativeConnection).exceptionHandler(exceptionHandler.capture());

        RuntimeException error = new RuntimeException("failed");
        closeHandler.getValue().handle(nativeConnection);
        droppedHandler.getValue().handle(nativeConnection);
        pingHandler.getValue().handle(nativeConnection);
        exceptionHandler.getValue().handle(error);

        assertThat(closed).isTrue();
        assertThat(dropped).isTrue();
        assertThat(ping).isTrue();
        assertThat(exception).hasValue(error);
        assertThat(internalConnectionLost).isTrue();
        assertThat(internalException).hasValue(error);
    }
}
