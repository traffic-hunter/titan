package org.traffichunter.titan.springframework.stomp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.traffichunter.titan.core.codec.stomp.StompFrames;
import org.traffichunter.titan.core.codec.stomp.StompHeaders.Elements;
import org.traffichunter.titan.core.transport.stomp.client.StompClient;
import org.traffichunter.titan.core.transport.stomp.client.StompClientOperations;
import org.traffichunter.titan.core.util.buffer.Buffer;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TitanTemplateTest {

    private StompClientOperations operations;
    private TitanTemplate template;
    private StompFrames frame;

    @BeforeEach
    void setUp() {
        StompClient client = mock(StompClient.class);
        operations = mock(StompClientOperations.class);
        frame = mock(StompFrames.class);

        when(client.operations()).thenReturn(operations);
        when(operations.isConnected()).thenReturn(true);
        template = new TitanTemplate(new TitanClientManager(client, new TitanProperties()));
    }

    @Test
    void sends_payload_through_operations() throws Exception {
        when(operations.send(eq("/topic/test"), any(Buffer.class)))
                .thenReturn(CompletableFuture.completedFuture(frame));

        StompFrames result = template.send("/topic/test", "hello");

        ArgumentCaptor<Buffer> payload = ArgumentCaptor.forClass(Buffer.class);
        verify(operations).send(eq("/topic/test"), payload.capture());
        assertThat(result).isSameAs(frame);
        assertThat(new String(payload.getValue().getBytes(), StandardCharsets.UTF_8)).isEqualTo("hello");
    }

    @Test
    void subscribes_through_operations() throws Exception {
        when(operations.subscribe(eq("/topic/test"), any()))
                .thenReturn(CompletableFuture.completedFuture("sub-1"));

        String subscriptionId = template.subscribe("/topic/test");

        assertThat(subscriptionId).isEqualTo("sub-1");
        verify(operations).subscribe(eq("/topic/test"), any());
    }

    @Test
    void unsubscribes_with_destination_as_subscription_id() throws Exception {
        when(operations.unsubscribe(eq("/topic/test"), any()))
                .thenReturn(CompletableFuture.completedFuture(frame));

        StompFrames result = template.unsubscribe("/topic/test");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<Elements, String>> headers = ArgumentCaptor.forClass(Map.class);
        verify(operations).unsubscribe(eq("/topic/test"), headers.capture());
        assertThat(result).isSameAs(frame);
        assertThat(headers.getValue()).containsEntry(Elements.ID, "/topic/test");
    }

    @Test
    void disconnects_through_operations() throws Exception {
        when(operations.disconnect()).thenReturn(CompletableFuture.completedFuture(frame));

        StompFrames result = template.disconnect();

        verify(operations).disconnect();
        assertThat(result).isSameAs(frame);
    }
}
