package org.traffichunter.titan.springframework.stomp.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.traffichunter.titan.core.codec.stomp.StompFrames;
import org.traffichunter.titan.client.TitanClient;
import org.traffichunter.titan.core.util.Handler;
import org.traffichunter.titan.core.util.buffer.Buffer;
import org.traffichunter.titan.springframework.stomp.TitanProperties;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TitanTemplateTest {

    private TitanClient client;
    private TitanTemplate template;
    private StompFrames frame;

    @BeforeEach
    void setUp() {
        client = mock(TitanClient.class);
        frame = mock(StompFrames.class);

        when(client.connect()).thenReturn(CompletableFuture.completedFuture(client));
        when(client.isConnected()).thenReturn(true);
        template = new TitanTemplate(new TitanClientManager(client, new TitanProperties()));
    }

    @Test
    void blocking_send_string_delegates_to_connection() throws Exception {
        when(client.send(eq("/topic/test"), any(Buffer.class)))
                .thenReturn(CompletableFuture.completedFuture(frame));

        StompFrames result = template.send("/topic/test", "hello");

        ArgumentCaptor<Buffer> payload = ArgumentCaptor.forClass(Buffer.class);
        verify(client).send(eq("/topic/test"), payload.capture());
        assertThat(result).isSameAs(frame);
        assertThat(new String(payload.getValue().getBytes(), StandardCharsets.UTF_8)).isEqualTo("hello");
    }

    @Test
    void blocking_send_bytes_delegates_to_connection() throws Exception {
        when(client.send(eq("/topic/test"), any(Buffer.class)))
                .thenReturn(CompletableFuture.completedFuture(frame));

        StompFrames result = template.send("/topic/test", "hello".getBytes(StandardCharsets.UTF_8));

        ArgumentCaptor<Buffer> payload = ArgumentCaptor.forClass(Buffer.class);
        verify(client).send(eq("/topic/test"), payload.capture());
        assertThat(result).isSameAs(frame);
        assertThat(new String(payload.getValue().getBytes(), StandardCharsets.UTF_8)).isEqualTo("hello");
    }

    @Test
    void blocking_send_byte_buffer_copies_remaining_bytes() throws Exception {
        when(client.send(eq("/topic/test"), any(Buffer.class)))
                .thenReturn(CompletableFuture.completedFuture(frame));

        ByteBuffer buffer = ByteBuffer.wrap("prefix-hello".getBytes(StandardCharsets.UTF_8));
        buffer.position("prefix-".length());

        StompFrames result = template.send("/topic/test", buffer);

        ArgumentCaptor<Buffer> payload = ArgumentCaptor.forClass(Buffer.class);
        verify(client).send(eq("/topic/test"), payload.capture());
        assertThat(result).isSameAs(frame);
        assertThat(new String(payload.getValue().getBytes(), StandardCharsets.UTF_8)).isEqualTo("hello");
    }

    @Test
    void async_send_buffer_returns_connection_future() {
        CompletableFuture<StompFrames> expected = CompletableFuture.completedFuture(frame);
        Buffer payload = Buffer.alloc("hello".getBytes(StandardCharsets.UTF_8));
        when(client.send("/topic/test", payload)).thenReturn(expected);

        CompletableFuture<StompFrames> result = template.send("/topic/test", payload);

        assertThat(result).isSameAs(expected);
    }

    @Test
    void connects_through_manager_when_no_connection_exists() throws Exception {
        when(client.isConnected()).thenReturn(false);
        when(client.send(eq("/topic/test"), any(Buffer.class)))
                .thenReturn(CompletableFuture.completedFuture(frame));

        StompFrames result = template.send("/topic/test", "hello");

        verify(client).start();
        verify(client).connect();
        assertThat(result).isSameAs(frame);
    }

    @Test
    void blocking_subscribe_delegates_to_connection() throws Exception {
        when(client.subscribe(eq("/topic/test"), any()))
                .thenReturn(CompletableFuture.completedFuture("sub-1"));

        String subscriptionId = template.subscribe("/topic/test");

        assertThat(subscriptionId).isEqualTo("sub-1");
        verify(client).subscribe(eq("/topic/test"), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void async_subscribe_returns_connection_future() {
        CompletableFuture<String> expected = CompletableFuture.completedFuture("sub-1");
        Handler<StompFrames> handler = frame -> { };
        when(client.subscribe("/topic/test", handler)).thenReturn(expected);

        CompletableFuture<String> result = template.subscribe("/topic/test", handler);

        assertThat(result).isSameAs(expected);
    }

    @Test
    void unsubscribe_uses_subscription_id() {
        CompletableFuture<StompFrames> expected = CompletableFuture.completedFuture(frame);
        when(client.unsubscribe("sub-1")).thenReturn(expected);

        CompletableFuture<StompFrames> result = template.unsubscribe("sub-1");

        assertThat(result).isSameAs(expected);
        verify(client).unsubscribe("sub-1");
    }

    @Test
    void ack_delegates_to_connection() {
        CompletableFuture<StompFrames> expected = CompletableFuture.completedFuture(frame);
        when(client.ack("msg-1")).thenReturn(expected);

        assertThat(template.ack("msg-1")).isSameAs(expected);
    }

    @Test
    void nack_delegates_to_connection() {
        CompletableFuture<StompFrames> expected = CompletableFuture.completedFuture(frame);
        when(client.nack("msg-1")).thenReturn(expected);

        assertThat(template.nack("msg-1")).isSameAs(expected);
    }

    @Test
    void disconnect_delegates_to_connection() {
        CompletableFuture<StompFrames> expected = CompletableFuture.completedFuture(frame);
        when(client.disconnect()).thenReturn(expected);

        CompletableFuture<StompFrames> result = template.disconnect();

        verify(client).disconnect();
        assertThat(result).isSameAs(expected);
    }

    @Test
    void rejects_invalid_destination_synchronously() {
        when(client.subscribe(any(), any(Handler.class)))
                .thenThrow(new IllegalArgumentException("Invalid routing key"));

        assertThatThrownBy(() -> template.subscribe("/queue/invalid destination"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid routing key");
    }
}
