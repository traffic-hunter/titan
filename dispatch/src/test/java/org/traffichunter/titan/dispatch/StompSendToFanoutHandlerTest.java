package org.traffichunter.titan.dispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.traffichunter.titan.core.channel.stomp.StompClientChannel;
import org.traffichunter.titan.core.channel.stomp.StompServerChannel;
import org.traffichunter.titan.core.channel.stomp.StompServerEvent;
import org.traffichunter.titan.core.channel.stomp.StompServerHandlerContext;
import org.traffichunter.titan.core.codec.stomp.StompCommand;
import org.traffichunter.titan.core.codec.stomp.StompFrame;
import org.traffichunter.titan.core.codec.stomp.StompHeaders;
import org.traffichunter.titan.core.codec.stomp.StompHeaders.Elements;
import org.traffichunter.titan.core.message.DispatchRejectException;
import org.traffichunter.titan.core.message.DispatchResult;
import org.traffichunter.titan.core.message.Message;
import org.traffichunter.titan.core.transport.stomp.option.StompServerOption;
import org.traffichunter.titan.core.util.DestinationGroups;
import org.traffichunter.titan.core.util.concurrent.EventExecutorService;
import org.traffichunter.titan.core.util.concurrent.Promise;

/**
 * @author yun
 */
class StompSendToFanoutHandlerTest {

    private DispatchGateway gateway;
    private StompServerHandlerContext context;
    private StompClientChannel connection;
    private StompSendToFanoutHandler handler;

    @BeforeEach
    void setUp() {
        gateway = mock(DispatchGateway.class);
        when(gateway.sparkDispatch(any(Message.class))).thenReturn(CompletableFuture.completedFuture(null));
        StompServerChannel server = mock(StompServerChannel.class);
        when(server.option()).thenReturn(StompServerOption.builder().build());
        context = new StompServerHandlerContext(server);
        connection = mock(StompClientChannel.class);
        when(connection.session()).thenReturn("producer");
        EventExecutorService executor = mock(EventExecutorService.class);
        when(executor.inEventLoop()).thenReturn(true);
        when(connection.send(any(StompFrame.class))).thenAnswer(invocation ->
                Promise.<StompFrame>newPromise(executor).success(invocation.getArgument(0)));
        handler = new StompSendToFanoutHandler(gateway);
    }

    @Test
    void group_header_sets_message_group() {
        handler.handle(new StompServerEvent(send("market"), connection), context);

        ArgumentCaptor<Message> dispatched = ArgumentCaptor.forClass(Message.class);
        verify(gateway).sparkDispatch(dispatched.capture());
        assertThat(dispatched.getValue().getGroup()).isEqualTo("market");
        assertThat(dispatched.getValue().getDestination().path()).isEqualTo("/topic/price");
    }

    @Test
    void missing_group_header_defaults() {
        handler.handle(new StompServerEvent(send(null), connection), context);

        ArgumentCaptor<Message> dispatched = ArgumentCaptor.forClass(Message.class);
        verify(gateway).sparkDispatch(dispatched.capture());
        assertThat(dispatched.getValue().getGroup()).isEqualTo(DestinationGroups.DEFAULT);
    }

    @Test
    void invalid_group_sends_error_and_never_dispatches() {
        handler.handle(new StompServerEvent(send("bad/name"), connection), context);

        ArgumentCaptor<StompFrame> sent = ArgumentCaptor.forClass(StompFrame.class);
        verify(connection).send(sent.capture());
        assertThat(sent.getValue().getCommand()).isEqualTo(StompCommand.ERROR);
        assertThat(sent.getValue().getHeader(Elements.MESSAGE)).isEqualTo("Wrong send.");
        verify(connection).close();
        verify(gateway, never()).sparkDispatch(any(Message.class));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void invalid_request_preserves_receipt_and_waits_for_error_write(boolean missingDestination) {
        EventExecutorService executor = mock(EventExecutorService.class);
        when(executor.inEventLoop()).thenReturn(true);
        Promise<StompFrame> write = Promise.newPromise(executor);
        when(connection.send(any(StompFrame.class))).thenReturn(write);
        StompFrame request = missingDestination
                ? StompFrame.create(StompHeaders.create(), StompCommand.SEND)
                : send("bad/name");
        request.addHeader(Elements.RECEIPT, "send-invalid");

        handler.handle(new StompServerEvent(request, connection), context);

        ArgumentCaptor<StompFrame> sent = ArgumentCaptor.forClass(StompFrame.class);
        verify(connection).send(sent.capture());
        assertThat(sent.getValue().getCommand()).isEqualTo(StompCommand.ERROR);
        assertThat(sent.getValue().getHeader(Elements.MESSAGE)).isEqualTo("Wrong send.");
        assertThat(sent.getValue().getHeader(Elements.RECEIPT_ID)).isEqualTo("send-invalid");
        verify(gateway, never()).sparkDispatch(any(Message.class));
        verify(connection, never()).close();

        write.success(sent.getValue());

        verify(connection).close();
    }

    @ParameterizedTest
    @EnumSource(value = DispatchResult.class, names = "ACCEPTED", mode = EnumSource.Mode.EXCLUDE)
    void queue_rejection_sends_numeric_code_and_receipt_id(DispatchResult result) {
        when(gateway.sparkDispatch(any(Message.class))).thenReturn(CompletableFuture.failedFuture(
                new DispatchRejectException(result, "Queue refused the message: {}", result)));
        StompFrame request = send(null);
        request.addHeader(Elements.RECEIPT, "send-123");

        handler.handle(new StompServerEvent(request, connection), context);

        ArgumentCaptor<StompFrame> sent = ArgumentCaptor.forClass(StompFrame.class);
        verify(connection).send(sent.capture());
        assertThat(sent.getValue().getCommand()).isEqualTo(StompCommand.ERROR);
        assertThat(sent.getValue().getHeader(Elements.MESSAGE)).isEqualTo(Integer.toString(result.code()));
        assertThat(sent.getValue().getHeader(Elements.RECEIPT_ID)).isEqualTo("send-123");
        assertThat(sent.getValue().getHeader(Elements.RECEIPT)).isNull();
        assertThat(new String(sent.getValue().body(), StandardCharsets.UTF_8))
                .isEqualTo("Queue refused the message: " + result);
        verify(connection).close();
    }

    @Test
    void synchronous_rejection_without_receipt_sends_error_without_receipt_id() {
        when(gateway.sparkDispatch(any(Message.class))).thenThrow(
                new DispatchRejectException(DispatchResult.CLOSED, "Queue is closed"));

        handler.handle(new StompServerEvent(send(null), connection), context);

        ArgumentCaptor<StompFrame> sent = ArgumentCaptor.forClass(StompFrame.class);
        verify(connection).send(sent.capture());
        assertThat(sent.getValue().getHeader(Elements.MESSAGE)).isEqualTo("1003");
        assertThat(sent.getValue().getHeader(Elements.RECEIPT_ID)).isNull();
        verify(connection).close();
    }

    @Test
    void nested_completion_exception_preserves_queue_rejection_code() {
        when(gateway.sparkDispatch(any(Message.class))).thenReturn(CompletableFuture.failedFuture(
                new CompletionException(new CompletionException(
                        new DispatchRejectException(DispatchResult.FULL, "Queue is full")))));

        handler.handle(new StompServerEvent(send(null), connection), context);

        ArgumentCaptor<StompFrame> sent = ArgumentCaptor.forClass(StompFrame.class);
        verify(connection).send(sent.capture());
        assertThat(sent.getValue().getHeader(Elements.MESSAGE)).isEqualTo("1000");
        verify(connection).close();
    }

    @Test
    void failure_without_admission_result_does_not_claim_queue_rejection() {
        when(gateway.sparkDispatch(any(Message.class))).thenReturn(CompletableFuture.failedFuture(
                new DispatchRejectException("Handler failed after enqueue")));
        StompFrame request = send(null);
        request.addHeader(Elements.RECEIPT, "send-unknown");

        handler.handle(new StompServerEvent(request, connection), context);

        ArgumentCaptor<StompFrame> sent = ArgumentCaptor.forClass(StompFrame.class);
        verify(connection).send(sent.capture());
        assertThat(sent.getValue().getHeader(Elements.MESSAGE)).isEqualTo("Failed to dispatch.");
        assertThat(sent.getValue().getHeader(Elements.RECEIPT_ID)).isEqualTo("send-unknown");
    }

    @Test
    void error_content_length_counts_utf8_bytes() {
        when(gateway.sparkDispatch(any(Message.class))).thenReturn(CompletableFuture.failedFuture(
                new DispatchRejectException(DispatchResult.FULL, "Queue \u03bb refused the message")));

        handler.handle(new StompServerEvent(send(null), connection), context);

        ArgumentCaptor<StompFrame> sent = ArgumentCaptor.forClass(StompFrame.class);
        verify(connection).send(sent.capture());
        assertThat(sent.getValue().getHeader(Elements.CONTENT_LENGTH))
                .isEqualTo(Integer.toString(sent.getValue().body().length));
        assertThat(new String(sent.getValue().body(), StandardCharsets.UTF_8))
                .isEqualTo("Queue \u03bb refused the message");
    }

    @Test
    void connection_stays_open_until_error_write_completes() {
        EventExecutorService executor = mock(EventExecutorService.class);
        when(executor.inEventLoop()).thenReturn(true);
        Promise<StompFrame> write = Promise.newPromise(executor);
        when(connection.send(any(StompFrame.class))).thenReturn(write);
        when(gateway.sparkDispatch(any(Message.class))).thenReturn(CompletableFuture.failedFuture(
                new DispatchRejectException(DispatchResult.FULL, "Queue is full")));

        handler.handle(new StompServerEvent(send(null), connection), context);

        verify(connection, never()).close();
        ArgumentCaptor<StompFrame> sent = ArgumentCaptor.forClass(StompFrame.class);
        verify(connection).send(sent.capture());
        write.success(sent.getValue());
        verify(connection).close();
    }

    @Test
    void failed_error_write_closes_connection() {
        EventExecutorService executor = mock(EventExecutorService.class);
        when(executor.inEventLoop()).thenReturn(true);
        Promise<StompFrame> write = Promise.newPromise(executor);
        when(connection.send(any(StompFrame.class))).thenReturn(write);
        when(gateway.sparkDispatch(any(Message.class))).thenReturn(CompletableFuture.failedFuture(
                new DispatchRejectException(DispatchResult.PAUSED, "Queue is paused")));

        handler.handle(new StompServerEvent(send(null), connection), context);

        verify(connection, never()).close();
        write.fail(new IllegalStateException("Socket write failed"));
        verify(connection).close();
    }

    @Test
    void already_failed_error_write_closes_connection() {
        EventExecutorService executor = mock(EventExecutorService.class);
        when(executor.inEventLoop()).thenReturn(true);
        Promise<StompFrame> write = Promise.newPromise(executor);
        write.fail(new IllegalStateException("Send failed"));
        when(connection.send(any(StompFrame.class))).thenReturn(write);
        when(gateway.sparkDispatch(any(Message.class))).thenReturn(CompletableFuture.failedFuture(
                new DispatchRejectException(DispatchResult.FULL, "Queue is full")));

        handler.handle(new StompServerEvent(send(null), connection), context);

        verify(connection).close();
    }

    @Test
    void accepted_message_sends_receipt() {
        StompFrame request = send(null);
        request.addHeader(Elements.RECEIPT, "send-accepted");

        handler.handle(new StompServerEvent(request, connection), context);

        ArgumentCaptor<StompFrame> sent = ArgumentCaptor.forClass(StompFrame.class);
        verify(connection).send(sent.capture());
        assertThat(sent.getValue().getCommand()).isEqualTo(StompCommand.RECEIPT);
        assertThat(sent.getValue().getHeader(Elements.RECEIPT_ID)).isEqualTo("send-accepted");
        verify(connection, never()).close();
    }

    private static StompFrame send(String group) {
        StompFrame frame = StompFrame.create(StompHeaders.create(), StompCommand.SEND, "hello".getBytes());
        frame.addHeader(Elements.DESTINATION, "/topic/price");
        if (group != null) {
            frame.addHeader(Elements.GROUP, group);
        }
        return frame;
    }
}
