/*
 * Copyright 2025 traffic-hunter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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
import static org.traffichunter.titan.core.codec.stomp.StompHeaders.Elements.RECEIPT;

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

    @ParameterizedTest(name = "restarts under load, transport={0}")
    @EnumSource(TitanSmokeTransport.class)
    @Timeout(value = 180, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void server_restarts_under_load_restore_only_active_subscriptions(
            TitanSmokeTransport transport,
            TitanRuntime runtime
    ) throws Exception {
        runtime.start(transport);
        String destination = "/topic/" + UUID.randomUUID();
        ConcurrentLinkedQueue<String> active = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<String> removed = new ConcurrentLinkedQueue<>();
        TitanClient subscriber = runtime.client();
        TitanClient producer = runtime.client();
        // The producer has its own connection, so wait for the server's receipt before sending.
        subscriber.subscribe(destination, Map.of(ID, "active", RECEIPT, "subscribe-active"), frame ->
                active.add(new String(frame.body(), StandardCharsets.UTF_8))).get(10, TimeUnit.SECONDS);
        subscriber.subscribe(destination, Map.of(ID, "removed", RECEIPT, "subscribe-removed"), frame ->
                removed.add(new String(frame.body(), StandardCharsets.UTF_8))).get(10, TimeUnit.SECONDS);
        producer.send(destination, "before").get(10, TimeUnit.SECONDS);
        await().atMost(10, TimeUnit.SECONDS).until(() -> active.contains("before") && removed.contains("before"));
        subscriber.unsubscribe("removed", Map.of(RECEIPT, "unsubscribe-removed")).get(10, TimeUnit.SECONDS);

        AtomicBoolean loading = new AtomicBoolean(true);
        Thread load = Thread.ofVirtual().start(() -> {
            for (long i = 0; loading.get(); i++) {
                try {
                    producer.send(destination, "load-" + i).get(1, TimeUnit.SECONDS);
                } catch (Exception ignored) {
                    // Sends fail while the server is down. The probes below check delivery.
                }
                try {
                    Thread.sleep(5);
                } catch (InterruptedException e) {
                    return;
                }
            }
        });

        try {
            for (int restart = 1; restart <= 5; restart++) {
                runtime.stop();
                await().atMost(10, TimeUnit.SECONDS).until(() -> !subscriber.isConnected());
                runtime.restart();
                await().atMost(15, TimeUnit.SECONDS)
                        .until(() -> subscriber.isConnected() && producer.isConnected());

                // A probe sent before the subscription is restored is not delivered, so keep sending new ones.
                String probe = "probe-" + restart + "-";
                int[] attempts = {0};
                await().alias("subscription restored after restart " + restart)
                        .atMost(10, TimeUnit.SECONDS)
                        .pollInterval(200, TimeUnit.MILLISECONDS)
                        .until(() -> {
                            producer.send(destination, probe + attempts[0]++);
                            return active.stream().anyMatch(body -> body.startsWith(probe));
                        });
            }
        } finally {
            loading.set(false);
            load.join(10_000);
        }

        assertThat(active).as("restored subscription").doesNotHaveDuplicates()
                .anyMatch(body -> body.startsWith("load-"));
        assertThat(removed).as("unsubscribed subscription").containsExactly("before");
    }

    @ParameterizedTest(name = "content-length payload, transport={0}, embeddedNul={1}")
    @CsvSource({"TCP, false", "TCP, true", "WS, false", "WS, true"})
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
