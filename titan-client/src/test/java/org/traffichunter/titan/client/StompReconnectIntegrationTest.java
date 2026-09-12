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
package org.traffichunter.titan.client;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.traffichunter.titan.core.channel.EventLoopGroups;
import org.traffichunter.titan.core.codec.stomp.StompFrames;
import org.traffichunter.titan.core.codec.stomp.StompHeaders.Elements;
import org.traffichunter.titan.core.resilience.retry.RetryPolicy;
import org.traffichunter.titan.core.util.buffer.Buffer;

@EnableStompServer
class StompReconnectIntegrationTest {

    private static final int SHUTDOWN_TIMEOUT_SECONDS = 20;

    @Test
    @Timeout(value = 20, unit = SECONDS)
    void client_reconnects_after_server_restart(StompTestServer testServer) throws Exception {
        DefaultTitanClient client = startClient(testServer);

        try {
            client.connect().get(3, SECONDS);
            await().atMost(10, SECONDS)
                    .untilAsserted(() -> assertThat(testServer.server().connection().connections()).hasSize(1));

            testServer.stop();
            await().atMost(10, SECONDS)
                    .untilAsserted(() -> assertThat(client.isConnected()).isFalse());

            testServer.restart();

            await().atMost(10, SECONDS)
                    .untilAsserted(() -> {
                        assertThat(testServer.server().connection().connections()).hasSize(1);
                        assertThat(client.isConnected()).isTrue();
                    });
        } finally {
            client.shutdown(SHUTDOWN_TIMEOUT_SECONDS, SECONDS);
        }
    }

    @Test
    @Timeout(value = 30, unit = SECONDS)
    void client_restores_subscription_and_receives_messages_after_server_restart(
            StompTestServer testServer
    ) throws Exception {
        DefaultTitanClient client = startClient(testServer);
        BlockingQueue<StompFrames> received = new LinkedBlockingQueue<>();
        String destination = "/queue/reconnect-subscription";
        String subscriptionId = "reconnect-subscription";

        try {
            client.connect().get(3, SECONDS);
            client.subscribe(
                    destination,
                    Map.of(Elements.ID, subscriptionId),
                    received::add
            ).get(3, SECONDS);

            client.send(destination, Buffer.heap().alloc("before-restart")).get(3, SECONDS);
            StompFrames beforeRestart = received.poll(3, SECONDS);
            assertThat(beforeRestart).isNotNull();
            assertThat(beforeRestart.body()).asString(StandardCharsets.UTF_8).isEqualTo("before-restart");
            assertThat(beforeRestart.getHeader(Elements.SUBSCRIPTION)).isEqualTo(subscriptionId);

            testServer.stop();
            await().atMost(10, SECONDS)
                    .untilAsserted(() -> assertThat(client.isConnected()).isFalse());

            testServer.restart();
            await().atMost(10, SECONDS)
                    .untilAsserted(() -> {
                        assertThat(testServer.server().connection().connections()).hasSize(1);
                        assertThat(client.isConnected()).isTrue();
                    });

            client.send(destination, Buffer.heap().alloc("after-restart")).get(3, SECONDS);
            StompFrames afterRestart = received.poll(3, SECONDS);
            assertThat(afterRestart).isNotNull();
            assertThat(afterRestart.body()).asString(StandardCharsets.UTF_8).isEqualTo("after-restart");
            assertThat(afterRestart.getHeader(Elements.SUBSCRIPTION)).isEqualTo(subscriptionId);
        } finally {
            client.shutdown(SHUTDOWN_TIMEOUT_SECONDS, SECONDS);
        }
    }

    @Test
    @Timeout(value = 30, unit = SECONDS)
    void client_does_not_restore_subscription_removed_before_server_restart(
            StompTestServer testServer
    ) throws Exception {
        DefaultTitanClient client = startClient(testServer);
        BlockingQueue<StompFrames> received = new LinkedBlockingQueue<>();
        String destination = "/queue/unsubscribed-before-reconnect";
        String subscriptionId = "removed-subscription";

        try {
            client.connect().get(3, SECONDS);
            client.subscribe(
                    destination,
                    Map.of(Elements.ID, subscriptionId),
                    received::add
            ).get(3, SECONDS);
            client.unsubscribe(subscriptionId).get(3, SECONDS);

            testServer.stop();
            await().atMost(10, SECONDS)
                    .untilAsserted(() -> assertThat(client.isConnected()).isFalse());

            testServer.restart();
            await().atMost(10, SECONDS)
                    .untilAsserted(() -> {
                        assertThat(testServer.server().connection().connections()).hasSize(1);
                        assertThat(client.isConnected()).isTrue();
                    });

            client.send(destination, Buffer.heap().alloc("must-not-be-delivered")).get(3, SECONDS);
            assertThat(received.poll(500, TimeUnit.MILLISECONDS)).isNull();
        } finally {
            client.shutdown(SHUTDOWN_TIMEOUT_SECONDS, SECONDS);
        }
    }

    @Test
    @Timeout(value = 30, unit = SECONDS)
    void client_restores_multiple_subscriptions_after_server_restart(
            StompTestServer testServer
    ) throws Exception {
        DefaultTitanClient client = startClient(testServer);
        BlockingQueue<StompFrames> firstMessages = new LinkedBlockingQueue<>();
        BlockingQueue<StompFrames> secondMessages = new LinkedBlockingQueue<>();

        try {
            client.connect().get(3, SECONDS);
            client.subscribe(
                    "/queue/reconnect-first",
                    Map.of(Elements.ID, "first-subscription"),
                    firstMessages::add
            ).get(3, SECONDS);
            client.subscribe(
                    "/queue/reconnect-second",
                    Map.of(Elements.ID, "second-subscription"),
                    secondMessages::add
            ).get(3, SECONDS);

            testServer.stop();
            await().atMost(10, SECONDS)
                    .untilAsserted(() -> assertThat(client.isConnected()).isFalse());
            testServer.restart();
            await().atMost(10, SECONDS)
                    .untilAsserted(() -> assertThat(client.isConnected()).isTrue());

            client.send("/queue/reconnect-first", Buffer.heap().alloc("first")).get(3, SECONDS);
            client.send("/queue/reconnect-second", Buffer.heap().alloc("second")).get(3, SECONDS);

            assertThat(firstMessages.poll(3, SECONDS)).isNotNull()
                    .extracting(frame -> new String(frame.body(), StandardCharsets.UTF_8))
                    .isEqualTo("first");
            assertThat(secondMessages.poll(3, SECONDS)).isNotNull()
                    .extracting(frame -> new String(frame.body(), StandardCharsets.UTF_8))
                    .isEqualTo("second");
        } finally {
            client.shutdown(SHUTDOWN_TIMEOUT_SECONDS, SECONDS);
        }
    }

    @Test
    @Timeout(value = 30, unit = SECONDS)
    void vertx_client_restores_subscription_after_server_restart(
            StompTestServer testServer
    ) throws Exception {
        ClientConfiguration configuration = reconnectConfiguration(testServer);
        DefaultTitanClient client = new DefaultTitanClient(new VertxStompClientDriver(configuration));
        BlockingQueue<StompFrames> received = new LinkedBlockingQueue<>();
        String destination = "/queue/vertx-reconnect";

        try {
            client.start();
            client.connect().get(3, SECONDS);
            client.subscribe(
                    destination,
                    Map.of(Elements.ID, "vertx-subscription"),
                    received::add
            ).get(3, SECONDS);

            testServer.stop();
            await().atMost(10, SECONDS)
                    .untilAsserted(() -> assertThat(client.isConnected()).isFalse());
            testServer.restart();
            await().atMost(10, SECONDS)
                    .untilAsserted(() -> assertThat(client.isConnected()).isTrue());

            client.send(destination, Buffer.heap().alloc("vertx-restored")).get(3, SECONDS);
            StompFrames restored = received.poll(3, SECONDS);
            assertThat(restored).isNotNull();
            assertThat(restored.body()).asString(StandardCharsets.UTF_8).isEqualTo("vertx-restored");
            assertThat(restored.getHeader(Elements.SUBSCRIPTION)).isEqualTo("vertx-subscription");
        } finally {
            client.shutdown(SHUTDOWN_TIMEOUT_SECONDS, SECONDS);
        }
    }

    @Test
    @Timeout(value = 30, unit = SECONDS)
    void client_retries_the_whole_connection_after_partial_subscription_restore_failure(
            StompTestServer testServer
    ) throws Exception {
        ClientConfiguration configuration = reconnectConfiguration(testServer);
        AtomicInteger restoredSubscriptions = new AtomicInteger();
        FaultInjectingDriver driver = new FaultInjectingDriver(
                new TitanStompClientDriver(EventLoopGroups.group(1), configuration),
                (attempt, connection) -> attempt == 2
                        ? interceptSubscriptions(connection, (delegate, destination, headers, handler) -> {
                            if (restoredSubscriptions.incrementAndGet() == 2) {
                                return CompletableFuture.failedFuture(new ClientException("Injected restore failure"));
                            }
                            return delegate.subscribe(destination, headers, handler);
                        })
                        : connection
        );
        DefaultTitanClient client = new DefaultTitanClient(driver);
        BlockingQueue<StompFrames> firstMessages = new LinkedBlockingQueue<>();
        BlockingQueue<StompFrames> secondMessages = new LinkedBlockingQueue<>();

        try {
            client.start();
            client.connect().get(3, SECONDS);
            client.subscribe(
                    "/queue/partial-first",
                    Map.of(Elements.ID, "partial-first"),
                    firstMessages::add
            ).get(3, SECONDS);
            client.subscribe(
                    "/queue/partial-second",
                    Map.of(Elements.ID, "partial-second"),
                    secondMessages::add
            ).get(3, SECONDS);

            testServer.stop();
            await().atMost(10, SECONDS)
                    .untilAsserted(() -> assertThat(client.isConnected()).isFalse());
            testServer.restart();
            await().atMost(10, SECONDS)
                    .untilAsserted(() -> {
                        assertThat(driver.connectionAttempts()).isGreaterThanOrEqualTo(3);
                        assertThat(client.isConnected()).isTrue();
                    });

            client.send("/queue/partial-first", Buffer.heap().alloc("first-restored")).get(3, SECONDS);
            client.send("/queue/partial-second", Buffer.heap().alloc("second-restored")).get(3, SECONDS);
            assertThat(firstMessages.poll(3, SECONDS)).isNotNull();
            assertThat(secondMessages.poll(3, SECONDS)).isNotNull();
        } finally {
            client.shutdown(SHUTDOWN_TIMEOUT_SECONDS, SECONDS);
        }
    }

    @Test
    @Timeout(value = 30, unit = SECONDS)
    void client_retries_when_subscription_restore_future_does_not_complete(
            StompTestServer testServer
    ) throws Exception {
        ClientConfiguration configuration = reconnectConfiguration(testServer, Duration.ofMillis(100));
        FaultInjectingDriver driver = new FaultInjectingDriver(
                new TitanStompClientDriver(EventLoopGroups.singleGroup(), configuration),
                (attempt, connection) -> attempt == 2
                        ? interceptSubscriptions(
                                connection,
                                (delegate, destination, headers, handler) -> new CompletableFuture<>()
                        )
                        : connection
        );
        DefaultTitanClient client = new DefaultTitanClient(driver);
        BlockingQueue<StompFrames> received = new LinkedBlockingQueue<>();
        String destination = "/queue/restore-timeout";

        try {
            client.start();
            client.connect().get(3, SECONDS);
            client.subscribe(
                    destination,
                    Map.of(Elements.ID, "timeout-subscription"),
                    received::add
            ).get(3, SECONDS);

            testServer.stop();
            await().atMost(10, SECONDS)
                    .untilAsserted(() -> assertThat(client.isConnected()).isFalse());
            testServer.restart();
            await().atMost(10, SECONDS)
                    .untilAsserted(() -> {
                        assertThat(driver.connectionAttempts()).isGreaterThanOrEqualTo(3);
                        assertThat(client.isConnected()).isTrue();
                    });

            client.send(destination, Buffer.heap().alloc("restored-after-timeout")).get(3, SECONDS);
            assertThat(received.poll(3, SECONDS)).isNotNull();
        } finally {
            client.shutdown(SHUTDOWN_TIMEOUT_SECONDS, SECONDS);
        }
    }

    @Test
    @Timeout(value = 30, unit = SECONDS)
    void client_applies_a_late_subscription_completion_to_the_reconnected_connection(
            StompTestServer testServer
    ) throws Exception {
        ClientConfiguration configuration = reconnectConfiguration(testServer);
        CompletableFuture<Void> subscriptionWritten = new CompletableFuture<>();
        CompletableFuture<Void> releaseSubscriptionResult = new CompletableFuture<>();
        FaultInjectingDriver driver = new FaultInjectingDriver(
                new TitanStompClientDriver(EventLoopGroups.singleGroup(), configuration),
                (attempt, connection) -> attempt == 1
                        ? interceptSubscriptions(connection, (delegate, destination, headers, handler) ->
                                delegate.subscribe(destination, headers, handler)
                                        .thenCompose(subscriptionId -> {
                                            subscriptionWritten.complete(null);
                                            return releaseSubscriptionResult.thenApply(ignored -> subscriptionId);
                                        }))
                        : connection
        );
        DefaultTitanClient client = new DefaultTitanClient(driver);
        BlockingQueue<StompFrames> received = new LinkedBlockingQueue<>();
        String destination = "/queue/late-subscription";

        try {
            client.start();
            client.connect().get(3, SECONDS);
            CompletableFuture<String> subscription = client.subscribe(
                    destination,
                    Map.of(Elements.ID, "late-subscription"),
                    received::add
            );
            subscriptionWritten.get(3, SECONDS);

            testServer.stop();
            await().atMost(10, SECONDS)
                    .untilAsserted(() -> assertThat(client.isConnected()).isFalse());
            testServer.restart();
            await().atMost(10, SECONDS)
                    .untilAsserted(() -> assertThat(client.isConnected()).isTrue());

            releaseSubscriptionResult.complete(null);
            subscription.get(3, SECONDS);
            client.send(destination, Buffer.heap().alloc("late-subscription-restored")).get(3, SECONDS);
            assertThat(received.poll(3, SECONDS)).isNotNull();
        } finally {
            releaseSubscriptionResult.complete(null);
            client.shutdown(SHUTDOWN_TIMEOUT_SECONDS, SECONDS);
        }
    }

    @Test
    @Timeout(value = 30, unit = SECONDS)
    void client_restores_a_grouped_subscription_after_server_restart(
            StompTestServer testServer
    ) throws Exception {
        DefaultTitanClient client = startClient(testServer);
        BlockingQueue<StompFrames> market = new LinkedBlockingQueue<>();
        BlockingQueue<StompFrames> plain = new LinkedBlockingQueue<>();
        String destination = "/queue/reconnect-group";

        try {
            client.connect().get(3, SECONDS);
            String marketId = client.subscribe("market", destination, market::add).get(3, SECONDS);
            client.subscribe(destination, plain::add).get(3, SECONDS);

            testServer.stop();
            await().atMost(10, SECONDS)
                    .untilAsserted(() -> assertThat(client.isConnected()).isFalse());

            testServer.restart();
            await().atMost(10, SECONDS)
                    .untilAsserted(() -> assertThat(client.isConnected()).isTrue());

            client.send("market", destination, "after-restart").get(3, SECONDS);

            StompFrames restored = market.poll(3, SECONDS);
            assertThat(restored).isNotNull();
            assertThat(restored.body()).asString(StandardCharsets.UTF_8).isEqualTo("after-restart");
            assertThat(restored.getHeader(Elements.SUBSCRIPTION)).isEqualTo(marketId);
            assertThat(restored.getHeader(Elements.GROUP)).isEqualTo("market");
            assertThat(plain.poll(500, TimeUnit.MILLISECONDS)).isNull();
        } finally {
            client.shutdown(SHUTDOWN_TIMEOUT_SECONDS, SECONDS);
        }
    }

    @Test
    @Timeout(value = 30, unit = SECONDS)
    void subscription_dropped_while_the_connection_is_down_is_not_restored(
            StompTestServer testServer
    ) throws Exception {
        DefaultTitanClient client = startClient(testServer);
        BlockingQueue<StompFrames> received = new LinkedBlockingQueue<>();
        String destination = "/queue/unsubscribed-while-offline";

        try {
            client.connect().get(3, SECONDS);
            String subscriptionId = client.subscribe("market", destination, received::add).get(3, SECONDS);

            testServer.stop();
            await().atMost(10, SECONDS)
                    .untilAsserted(() -> assertThat(client.isConnected()).isFalse());

            // No frame can be sent, so the call fails; the subscription still has to go away.
            assertThat(client.unsubscribe(subscriptionId)).failsWithin(3, SECONDS);

            testServer.restart();
            await().atMost(10, SECONDS)
                    .untilAsserted(() -> assertThat(client.isConnected()).isTrue());

            client.send("market", destination, "must-not-be-delivered").get(3, SECONDS);
            assertThat(received.poll(500, TimeUnit.MILLISECONDS)).isNull();
        } finally {
            client.shutdown(SHUTDOWN_TIMEOUT_SECONDS, SECONDS);
        }
    }

    @Test
    @Timeout(value = 30, unit = SECONDS)
    void a_header_map_edited_before_the_subscribe_completes_keeps_its_group(
            StompTestServer testServer
    ) throws Exception {
        CompletableFuture<Void> subscriptionWritten = new CompletableFuture<>();
        CompletableFuture<Void> releaseSubscription = new CompletableFuture<>();
        CompletableFuture<Map<Elements, String>> restored = new CompletableFuture<>();
        FaultInjectingDriver driver = new FaultInjectingDriver(
                new TitanStompClientDriver(
                        EventLoopGroups.singleGroup(),
                        reconnectConfiguration(testServer)
                ),
                (attempt, connection) -> attempt == 1
                        ? interceptSubscriptions(connection, (delegate, destination, headers, handler) ->
                                delegate.subscribe(destination, headers, handler)
                                        .thenCompose(subscriptionId -> {
                                            subscriptionWritten.complete(null);
                                            return releaseSubscription.thenApply(ignored -> subscriptionId);
                                        }))
                        : interceptSubscriptions(connection, (delegate, destination, headers, handler) -> {
                            restored.complete(Map.copyOf(headers));
                            return delegate.subscribe(destination, headers, handler);
                        })
        );
        DefaultTitanClient client = new DefaultTitanClient(driver);
        String destination = "/queue/edited-headers";
        Map<Elements, String> headers = new HashMap<>();
        headers.put(Elements.GROUP, "market");

        try {
            client.start();
            client.connect().get(3, SECONDS);
            CompletableFuture<String> subscription = client.subscribe(destination, headers, frame -> { });
            subscriptionWritten.get(3, SECONDS);

            // The subscription kept for reconnect used to be built from this map once the
            // SUBSCRIBE completed, so an edit landing in this window chose the restored group.
            headers.put(Elements.GROUP, "notification");
            releaseSubscription.complete(null);
            String subscriptionId = subscription.get(3, SECONDS);

            testServer.stop();
            await().atMost(10, SECONDS)
                    .untilAsserted(() -> assertThat(client.isConnected()).isFalse());
            testServer.restart();
            await().atMost(10, SECONDS)
                    .untilAsserted(() -> assertThat(client.isConnected()).isTrue());

            assertThat(restored.get(3, SECONDS))
                    .containsEntry(Elements.GROUP, "market")
                    .containsEntry(Elements.ID, subscriptionId);
        } finally {
            releaseSubscription.complete(null);
            client.shutdown(SHUTDOWN_TIMEOUT_SECONDS, SECONDS);
        }
    }

    @Test
    @Timeout(value = 40, unit = SECONDS)
    void a_subscription_dropped_while_another_one_is_being_restored_is_not_restored(
            StompTestServer testServer
    ) throws Exception {
        HeldRestore held = new HeldRestore();
        DefaultTitanClient client = new DefaultTitanClient(held.driver(testServer));
        BlockingQueue<StompFrames> market = new LinkedBlockingQueue<>();
        BlockingQueue<StompFrames> notification = new LinkedBlockingQueue<>();
        String destination = "/queue/dropped-during-restore";

        try {
            client.start();
            client.connect().get(3, SECONDS);
            String marketId = client.subscribe("market", destination, market::add).get(3, SECONDS);
            String notificationId =
                    client.subscribe("notification", destination, notification::add).get(3, SECONDS);

            testServer.stop();
            await().atMost(10, SECONDS)
                    .untilAsserted(() -> assertThat(client.isConnected()).isFalse());
            testServer.restart();

            // One subscription of the snapshot is on the wire. The other has not been sent yet,
            // and dropping it now must keep the replay from sending it at all.
            String holding = held.reached();
            String dropped = holding.equals(marketId) ? notificationId : marketId;
            client.unsubscribe(dropped);
            held.release();

            await().atMost(10, SECONDS)
                    .untilAsserted(() -> assertThat(client.isConnected()).isTrue());

            client.send("market", destination, "m1").get(3, SECONDS);
            client.send("notification", destination, "n1").get(3, SECONDS);

            boolean marketHeld = holding.equals(marketId);
            assertThat((marketHeld ? market : notification).poll(3, SECONDS)).isNotNull();
            assertThat((marketHeld ? notification : market).poll(500, TimeUnit.MILLISECONDS)).isNull();
        } finally {
            held.release();
            client.shutdown(SHUTDOWN_TIMEOUT_SECONDS, SECONDS);
        }
    }

    @Test
    @Timeout(value = 40, unit = SECONDS)
    void a_subscription_dropped_while_its_own_restore_is_in_flight_is_undone(
            StompTestServer testServer
    ) throws Exception {
        HeldRestore held = new HeldRestore();
        DefaultTitanClient client = new DefaultTitanClient(held.driver(testServer));
        BlockingQueue<StompFrames> market = new LinkedBlockingQueue<>();
        BlockingQueue<StompFrames> notification = new LinkedBlockingQueue<>();
        String destination = "/queue/dropped-mid-restore";

        try {
            client.start();
            client.connect().get(3, SECONDS);
            String marketId = client.subscribe("market", destination, market::add).get(3, SECONDS);
            String notificationId =
                    client.subscribe("notification", destination, notification::add).get(3, SECONDS);

            testServer.stop();
            await().atMost(10, SECONDS)
                    .untilAsserted(() -> assertThat(client.isConnected()).isFalse());
            testServer.restart();

            // Dropped while its own SUBSCRIBE is on the wire. Only the replay knows the server
            // is about to accept it, so only the replay can take it back.
            String holding = held.reached();
            client.unsubscribe(holding);
            held.release();

            await().atMost(10, SECONDS)
                    .untilAsserted(() -> assertThat(client.isConnected()).isTrue());

            client.send("market", destination, "m1").get(3, SECONDS);
            client.send("notification", destination, "n1").get(3, SECONDS);

            boolean marketHeld = holding.equals(marketId);
            assertThat(holding).isIn(marketId, notificationId);
            assertThat((marketHeld ? market : notification).poll(500, TimeUnit.MILLISECONDS)).isNull();
            assertThat((marketHeld ? notification : market).poll(3, SECONDS)).isNotNull();
        } finally {
            held.release();
            client.shutdown(SHUTDOWN_TIMEOUT_SECONDS, SECONDS);
        }
    }

    private static DefaultTitanClient startClient(StompTestServer testServer) {
        DefaultTitanClient client = new DefaultTitanClient(
                new TitanStompClientDriver(EventLoopGroups.singleGroup(), reconnectConfiguration(testServer))
        );
        client.start();
        return client;
    }

    private static ClientConfiguration reconnectConfiguration(StompTestServer testServer) {
        return ClientConfiguration.builder()
                .host(testServer.host())
                .port(testServer.port())
                .reconnectPolicy(RetryPolicy.fixed(
                        RetryPolicy.UNLIMITED_ATTEMPTS,
                        Duration.ofMillis(10)
                ))
                .build();
    }

    private static ClientConfiguration reconnectConfiguration(
            StompTestServer testServer,
            Duration connectTimeout
    ) {
        ClientConfiguration configuration = reconnectConfiguration(testServer);
        return new ClientConfiguration(
                configuration.host(),
                configuration.port(),
                configuration.session(),
                configuration.inet(),
                connectTimeout,
                configuration.reconnectPolicy(),
                configuration.reconnectListener(),
                configuration.tlsContext(),
                configuration.webSocketPath()
        );
    }

    /**
     * Holds the first SUBSCRIBE of a reconnect's replay until the test lets it go.
     *
     * <p>That pause is the window the tests need: the snapshot has been taken and is being
     * replayed, so an unsubscribe issued now reaches a subscription the replay is about to
     * recreate.</p>
     */
    private static final class HeldRestore {

        private final CompletableFuture<String> reached = new CompletableFuture<>();
        private final CompletableFuture<Void> release = new CompletableFuture<>();

        private FaultInjectingDriver driver(StompTestServer testServer) {
            return new FaultInjectingDriver(
                    new TitanStompClientDriver(
                            EventLoopGroups.singleGroup(),
                            reconnectConfiguration(testServer)
                    ),
                    (attempt, connection) -> attempt == 1
                            ? connection
                            : interceptSubscriptions(connection, (delegate, destination, headers, handler) -> {
                                if (reached.complete(headers.get(Elements.ID))) {
                                    return release.thenCompose(ignored ->
                                            delegate.subscribe(destination, headers, handler));
                                }
                                return delegate.subscribe(destination, headers, handler);
                            })
            );
        }

        private String reached() throws Exception {
            return reached.get(15, SECONDS);
        }

        private void release() {
            release.complete(null);
        }
    }

    private static StompConnection interceptSubscriptions(
            StompConnection connection,
            SubscriptionInterceptor interceptor
    ) {
        return (StompConnection) Proxy.newProxyInstance(
                StompConnection.class.getClassLoader(),
                new Class<?>[]{StompConnection.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("subscribe") && arguments != null && arguments.length == 3) {
                        @SuppressWarnings("unchecked")
                        Map<Elements, String> headers = (Map<Elements, String>) arguments[1];
                        @SuppressWarnings("unchecked")
                        org.traffichunter.titan.core.util.Handler<StompFrames> handler =
                                (org.traffichunter.titan.core.util.Handler<StompFrames>) arguments[2];
                        return interceptor.subscribe(connection, (String) arguments[0], headers, handler);
                    }

                    try {
                        return method.invoke(connection, arguments);
                    } catch (InvocationTargetException error) {
                        throw error.getCause();
                    }
                }
        );
    }

    @FunctionalInterface
    private interface SubscriptionInterceptor {

        CompletableFuture<String> subscribe(
                StompConnection connection,
                String destination,
                Map<Elements, String> headers,
                org.traffichunter.titan.core.util.Handler<StompFrames> handler
        );
    }

    private static final class FaultInjectingDriver implements StompClientDriver {

        private final StompClientDriver delegate;
        private final BiFunction<Integer, StompConnection, StompConnection> connectionDecorator;
        private final AtomicInteger connectionAttempts = new AtomicInteger();

        private FaultInjectingDriver(
                StompClientDriver delegate,
                BiFunction<Integer, StompConnection, StompConnection> connectionDecorator
        ) {
            this.delegate = delegate;
            this.connectionDecorator = connectionDecorator;
        }

        @Override
        public String name() {
            return delegate.name();
        }

        @Override
        public void start() {
            delegate.start();
        }

        @Override
        public ClientConfiguration clientConfiguration() {
            return delegate.clientConfiguration();
        }

        @Override
        public Worker worker() {
            return delegate.worker();
        }

        @Override
        public CompletableFuture<StompConnection> connect(InetSocketAddress remoteAddress) throws ClientException {
            return delegate.connect(remoteAddress)
                    .thenApply(connection -> connectionDecorator.apply(
                            connectionAttempts.incrementAndGet(),
                            connection
                    ));
        }

        @Override
        public void close(long timeout, TimeUnit unit) {
            delegate.close(timeout, unit);
        }

        private int connectionAttempts() {
            return connectionAttempts.get();
        }
    }
}
