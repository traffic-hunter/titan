/*
The MIT License

Copyright (c) 2025 traffic-hunter

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
*/
package org.traffichunter.titan.smoke.titan;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.traffichunter.titan.client.TitanClient;
import org.traffichunter.titan.core.codec.stomp.StompFrames;
import org.traffichunter.titan.core.util.buffer.Buffer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.traffichunter.titan.core.codec.stomp.StompHeaders.Elements.CONTENT_LENGTH;
import static org.traffichunter.titan.core.codec.stomp.StompHeaders.Elements.ID;

/**
 * Exercises packaged Titan messaging through TCP and WebSocket transports.
 *
 * @author yun
 */
@TitanSmokeTest
@Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class TitanMessagingSmokeTest {

    @ParameterizedTest(name = "concurrent fanout, transport={0}")
    @EnumSource(TitanSmokeTransport.class)
    void concurrent_producers_deliver_each_message_to_every_subscriber(
            TitanSmokeTransport transport,
            TitanRuntime runtime
    ) throws Exception {
        runtime.start(transport);
        String destination = "/topic/" + UUID.randomUUID();
        LinkedBlockingQueue<String> first = new LinkedBlockingQueue<>();
        LinkedBlockingQueue<String> second = new LinkedBlockingQueue<>();
        runtime.client().subscribe(destination, frame ->
                first.add(new String(frame.body(), StandardCharsets.UTF_8))).get(10, TimeUnit.SECONDS);
        runtime.client().subscribe(destination, frame ->
                second.add(new String(frame.body(), StandardCharsets.UTF_8))).get(10, TimeUnit.SECONDS);

        List<TitanClient> producers = new ArrayList<>();
        Set<String> expected = new HashSet<>();
        for (int producer = 0; producer < 3; producer++) {
            producers.add(runtime.client());
            for (int sequence = 0; sequence < 20; sequence++) {
                expected.add(producer + ":" + sequence);
            }
        }
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(3)) {
            List<CompletableFuture<Void>> tasks = new ArrayList<>();
            for (int producer = 0; producer < producers.size(); producer++) {
                int id = producer;
                tasks.add(CompletableFuture.runAsync(() -> {
                    List<CompletableFuture<StompFrames>> sends = new ArrayList<>();
                    for (int sequence = 0; sequence < 20; sequence++) {
                        sends.add(producers.get(id).send(destination, id + ":" + sequence));
                    }
                    CompletableFuture.allOf(sends.toArray(CompletableFuture[]::new))
                            .orTimeout(10, TimeUnit.SECONDS).join();
                }, executor));
            }
            CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new)).get(15, TimeUnit.SECONDS);
        }

        for (LinkedBlockingQueue<String> messages : List.of(first, second)) {
            List<String> received = new ArrayList<>();
            for (int i = 0; i < expected.size(); i++) {
                String message = messages.poll(10, TimeUnit.SECONDS);
                assertThat(message).as("fanout message %s", i).isNotNull();
                received.add(message);
            }
            assertThat(received).containsExactlyInAnyOrderElementsOf(expected);
            assertThat(messages.poll(200, TimeUnit.MILLISECONDS)).as("duplicate delivery").isNull();
        }
    }

    @ParameterizedTest(name = "payload boundaries, transport={0}")
    @EnumSource(TitanSmokeTransport.class)
    void empty_unicode_and_large_payloads_arrive_unchanged(
            TitanSmokeTransport transport,
            TitanRuntime runtime
    ) throws Exception {
        runtime.start(transport);
        String destination = "/queue/" + UUID.randomUUID();
        LinkedBlockingQueue<StompFrames> messages = new LinkedBlockingQueue<>();
        TitanClient producer = runtime.client();
        runtime.client().subscribe(destination, messages::add).get(10, TimeUnit.SECONDS);

        for (String payload : List.of("", "\uD55C\uAE00-\uD83D\uDE80", "x".repeat(48 * 1024))) {
            producer.send(destination, payload).get(10, TimeUnit.SECONDS);
            StompFrames received = messages.poll(10, TimeUnit.SECONDS);
            assertThat(received).as("payload bytes=%s", payload.getBytes(StandardCharsets.UTF_8).length)
                    .isNotNull();
            assertThat(received.body()).isEqualTo(payload.getBytes(StandardCharsets.UTF_8));
        }
    }

    @ParameterizedTest(name = "restart and resubscribe, transport={0}")
    @EnumSource(TitanSmokeTransport.class)
    void server_restart_restores_only_active_subscriptions(
            TitanSmokeTransport transport,
            TitanRuntime runtime
    ) throws Exception {
        runtime.start(transport);
        String destination = "/topic/" + UUID.randomUUID();
        LinkedBlockingQueue<String> active = new LinkedBlockingQueue<>();
        LinkedBlockingQueue<String> removed = new LinkedBlockingQueue<>();
        TitanClient client = runtime.client();
        client.subscribe(destination, Map.of(ID, "active"), frame ->
                active.add(new String(frame.body(), StandardCharsets.UTF_8))).get(10, TimeUnit.SECONDS);
        client.subscribe(destination, Map.of(ID, "removed"), frame ->
                removed.add(new String(frame.body(), StandardCharsets.UTF_8))).get(10, TimeUnit.SECONDS);
        client.send(destination, "before").get(10, TimeUnit.SECONDS);
        assertThat(active.poll(10, TimeUnit.SECONDS)).isEqualTo("before");
        assertThat(removed.poll(10, TimeUnit.SECONDS)).isEqualTo("before");

        client.unsubscribe("removed").get(10, TimeUnit.SECONDS);
        runtime.stop();
        await().atMost(10, TimeUnit.SECONDS).until(() -> !client.isConnected());
        runtime.restart();
        await().atMost(15, TimeUnit.SECONDS).until(client::isConnected);

        client.send(destination, "after").get(10, TimeUnit.SECONDS);
        assertThat(active.poll(10, TimeUnit.SECONDS)).isEqualTo("after");
        assertThat(removed.poll(300, TimeUnit.MILLISECONDS)).isNull();
        assertThat(active.poll(200, TimeUnit.MILLISECONDS)).as("duplicate restored subscription").isNull();
    }

    @ParameterizedTest(name = "content-length payload, transport={0}, embeddedNul={1}")
    @CsvSource({"TCP, false", "TCP, true", "WEBSOCKET, false", "WEBSOCKET, true"})
    void content_length_preserves_line_breaks_and_embedded_nul(
            TitanSmokeTransport transport,
            boolean embeddedNul,
            TitanRuntime runtime
    ) throws Exception {
        runtime.start(transport);
        String destination = "/queue/" + UUID.randomUUID();
        LinkedBlockingQueue<StompFrames> messages = new LinkedBlockingQueue<>();
        TitanClient producer = runtime.client();
        runtime.client().subscribe(destination, messages::add).get(10, TimeUnit.SECONDS);

        byte[] payload = embeddedNul
                ? new byte[]{'a', 0, 'b'}
                : "first\nsecond\r\nthird\n".getBytes(StandardCharsets.UTF_8);
        LinkedBlockingQueue<String> errors = new LinkedBlockingQueue<>();
        producer.errorHandler(frame -> errors.add(new String(frame.body(), StandardCharsets.UTF_8)));
        producer.connectionDroppedHandler(ignored -> errors.add("Producer connection dropped for valid payload"));
        producer.send(destination, Buffer.heap().alloc(payload),
                Map.of(CONTENT_LENGTH, Integer.toString(payload.length))).get(10, TimeUnit.SECONDS);
        await().alias("content-length payload delivery").atMost(10, TimeUnit.SECONDS)
                .until(() -> !messages.isEmpty() || !errors.isEmpty());
        assertThat(errors).as("valid payload must not fail the connection").isEmpty();
        StompFrames received = messages.poll();
        assertThat(received).as("content-length payload must be delivered intact").isNotNull();
        assertThat(received.body()).isEqualTo(payload);
    }
}
