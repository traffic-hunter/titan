package org.traffichunter.titan.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.traffichunter.titan.core.codec.stomp.StompFrames;
import org.traffichunter.titan.core.codec.stomp.StompHeaders.Elements;
import org.traffichunter.titan.core.util.Handler;
import org.traffichunter.titan.core.util.buffer.Buffer;

/**
 * Covers the group overloads on {@link TitanClient}, which turn a group argument into the
 * headers the transport actually carries.
 */
class GroupApiTest {

    private final RecordingClient client = new RecordingClient();

    @Test
    void named_group_becomes_a_group_header() {
        client.send("market", "/queue/price", Buffer.heap().alloc("m1"));

        assertThat(client.sentHeaders).containsExactly(Map.of(Elements.GROUP, "market"));
    }

    @Test
    void blank_group_sends_no_group_header() {
        client.send("  ", "/queue/price", Buffer.heap().alloc("m1"));
        client.send("default", "/queue/price", Buffer.heap().alloc("m2"));

        assertThat(client.sentHeaders).containsExactly(Map.of(), Map.of());
    }

    @Test
    void group_is_added_beside_the_headers_the_caller_gave() {
        client.send(
                "market",
                "/queue/price",
                Buffer.heap().alloc("m1"),
                Map.of(Elements.RECEIPT, "send-1")
        );

        assertThat(client.sentHeaders).containsExactly(
                Map.of(Elements.GROUP, "market", Elements.RECEIPT, "send-1")
        );
    }

    @Test
    void malformed_group_fails_the_send_and_consumes_the_payload() {
        Buffer payload = Buffer.heap().alloc("m1");

        CompletableFuture<StompFrames> result = client.send("bad/name", "/queue/price", payload);

        assertThatThrownBy(result::join).hasCauseInstanceOf(IllegalArgumentException.class);
        assertThat(payload.byteBuf().refCnt()).isZero();
        assertThat(client.sentHeaders).isEmpty();
    }

    @Test
    void group_over_64_characters_is_refused() {
        Buffer payload = Buffer.heap().alloc("m1");

        CompletableFuture<StompFrames> result =
                client.send("g".repeat(65), "/queue/price", payload);

        assertThatThrownBy(result::join).hasCauseInstanceOf(IllegalArgumentException.class);
        assertThat(payload.byteBuf().refCnt()).isZero();
    }

    @Test
    void group_argument_disagreeing_with_a_group_header_is_refused() {
        Buffer payload = Buffer.heap().alloc("m1");

        CompletableFuture<StompFrames> result = client.send(
                "market",
                "/queue/price",
                payload,
                Map.of(Elements.GROUP, "notification")
        );

        assertThatThrownBy(result::join)
                .hasCauseInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("notification");
        assertThat(payload.byteBuf().refCnt()).isZero();
        assertThat(client.sentHeaders).isEmpty();
    }

    @Test
    void group_argument_matching_the_group_header_is_accepted() {
        client.send(
                "market",
                "/queue/price",
                Buffer.heap().alloc("m1"),
                Map.of(Elements.GROUP, "market")
        );

        assertThat(client.sentHeaders).containsExactly(Map.of(Elements.GROUP, "market"));
    }

    @Test
    void the_header_map_the_caller_passed_is_left_alone() {
        Map<Elements, String> headers = new HashMap<>();
        headers.put(Elements.RECEIPT, "send-1");

        client.send("market", "/queue/price", Buffer.heap().alloc("m1"), headers);
        client.subscribe("market", "/queue/price", headers, frame -> { });

        assertThat(headers).isEqualTo(Map.of(Elements.RECEIPT, "send-1"));
    }

    @Test
    void every_subscription_gets_its_own_identifier() {
        String first = client.subscribe("market", "/queue/price", frame -> { }).join();
        String second = client.subscribe("market", "/queue/price", frame -> { }).join();
        String other = client.subscribe("notification", "/queue/price", frame -> { }).join();

        assertThat(first).isNotEqualTo(second).isNotEqualTo(other);
        assertThat(second).isNotEqualTo(other);
        assertThat(client.subscribedHeaders)
                .allSatisfy(headers -> assertThat(headers).containsKey(Elements.ID));
    }

    @Test
    void an_identifier_the_caller_chose_is_kept() {
        String id = client.subscribe(
                "market",
                "/queue/price",
                Map.of(Elements.ID, "sub-1"),
                frame -> { }
        ).join();

        assertThat(id).isEqualTo("sub-1");
        assertThat(client.subscribedHeaders)
                .containsExactly(Map.of(Elements.ID, "sub-1", Elements.GROUP, "market"));
    }

    @Test
    void malformed_group_fails_the_subscribe() {
        CompletableFuture<String> result = client.subscribe("bad name", "/queue/price", frame -> { });

        assertThatThrownBy(result::join).hasCauseInstanceOf(IllegalArgumentException.class);
        assertThat(client.subscribedHeaders).isEmpty();
    }

    /** Records what the group overloads hand down to the header-based API. */
    private static final class RecordingClient implements TitanClient {

        private final List<Map<Elements, String>> sentHeaders = new ArrayList<>();
        private final List<Map<Elements, String>> subscribedHeaders = new ArrayList<>();

        @Override
        public CompletableFuture<StompFrames> send(String destination, Buffer payload) {
            return send(destination, payload, Map.of());
        }

        @Override
        public CompletableFuture<StompFrames> send(
                String destination,
                Buffer payload,
                Map<Elements, String> headers
        ) {
            payload.release();
            sentHeaders.add(copy(headers));
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<String> subscribe(String destination, Handler<StompFrames> handler) {
            return subscribe(destination, Map.of(), handler);
        }

        @Override
        public CompletableFuture<String> subscribe(
                String destination,
                Map<Elements, String> headers,
                Handler<StompFrames> handler
        ) {
            subscribedHeaders.add(copy(headers));
            return CompletableFuture.completedFuture(headers.getOrDefault(Elements.ID, destination));
        }

        private static Map<Elements, String> copy(Map<Elements, String> headers) {
            Map<Elements, String> copied = new EnumMap<>(Elements.class);
            copied.putAll(headers);
            return copied;
        }

        @Override
        public String name() {
            return "recording";
        }

        @Override
        public void start() {
        }

        @Override
        public CompletableFuture<TitanClient> connect() {
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public CompletableFuture<StompFrames> unsubscribe(String subscriptionId) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<StompFrames> unsubscribe(
                String subscriptionId,
                Map<Elements, String> headers
        ) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<StompFrames> ack(String messageId) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<StompFrames> nack(String messageId) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<StompFrames> disconnect() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public TitanClient errorHandler(Handler<StompFrames> handler) {
            return this;
        }

        @Override
        public TitanClient closeHandler(Handler<TitanClient> handler) {
            return this;
        }

        @Override
        public TitanClient connectionDroppedHandler(Handler<TitanClient> handler) {
            return this;
        }

        @Override
        public TitanClient pingHandler(Handler<TitanClient> handler) {
            return this;
        }

        @Override
        public TitanClient exceptionHandler(Handler<Throwable> handler) {
            return this;
        }

        @Override
        public boolean isConnected() {
            return true;
        }

        @Override
        public boolean isStarted() {
            return true;
        }

        @Override
        public boolean isShutdown() {
            return false;
        }

        @Override
        public void shutdown(long timeout, TimeUnit unit) {
        }
    }
}
