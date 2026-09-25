package org.traffichunter.titan.dispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;
import org.traffichunter.titan.core.message.Message;
import org.traffichunter.titan.core.util.Destination;
import org.traffichunter.titan.core.util.buffer.Buffer;
import org.traffichunter.titan.dispatch.exporter.DispatchExporter;

/**
 * Covers queue management across destination groups: a change names one group and must
 * leave the queues of every other group, including the default one, untouched.
 */
class DispatchGatewayGroupManagementTest {

    private static final Destination DESTINATION = Destination.create("/queue/group-management");

    private final DestinationGroupRegistry registry = new DestinationGroupRegistry();
    private final Map<String, Integer> exported = new ConcurrentHashMap<>();
    private final VirtualThreadExecutorDispatchGateway gateway =
            new VirtualThreadExecutorDispatchGateway(recordingExporter(exported), registry);

    @AfterEach
    void closeGateway() {
        gateway.close();
    }

    @Test
    void creation_puts_the_queue_in_the_named_group_only() {
        DispatcherQueue queue = gateway.createQueue("market", DESTINATION, 1024);

        assertThat(queue.getGroup()).isEqualTo("market");
        assertThat(registry.get("market", DESTINATION)).isSameAs(queue);
        assertThat(registry.get(DESTINATION)).isNull();
    }

    @Test
    void creation_is_idempotent_per_group_and_keeps_the_first_byte_limit() {
        DispatcherQueue first = gateway.createQueue("market", DESTINATION, 1024);

        DispatcherQueue again = gateway.createQueue("market", DESTINATION, 4096);
        DispatcherQueue other = gateway.createQueue("notification", DESTINATION, 4096);

        assertThat(again).isSameAs(first);
        assertThat(again.getMaxPendingBytes()).isEqualTo(1024);
        assertThat(other).isNotSameAs(first);
        assertThat(other.getMaxPendingBytes()).isEqualTo(4096);
    }

    @Test
    void a_pause_leaves_the_other_groups_running() {
        DispatcherQueue market = gateway.createQueue("market", DESTINATION, 1024);
        DispatcherQueue plain = gateway.createQueue("default", DESTINATION, 1024);

        assertThat(gateway.pauseQueue("market", DESTINATION)).isTrue();

        assertThat(market.isPaused()).isTrue();
        assertThat(plain.isPaused()).isFalse();

        assertThat(gateway.resumeQueue("market", DESTINATION)).isTrue();
        assertThat(market.isPaused()).isFalse();
    }

    @Test
    void a_purge_empties_the_named_group_only() {
        // No consumer is attached to a queue made this way, so the messages simply stay.
        DispatcherQueue market = gateway.createQueue("market", DESTINATION, 4096);
        DispatcherQueue plain = gateway.createQueue("default", DESTINATION, 4096);
        market.enqueue(message("market"));
        plain.enqueue(message("default"));

        assertThat(gateway.purgeQueue("market", DESTINATION)).isTrue();

        assertThat(market.size()).isZero();
        assertThat(plain.size()).isEqualTo(1);
    }

    @Test
    void a_delete_removes_the_named_group_queue_only() {
        gateway.createQueue("market", DESTINATION, 1024);
        DispatcherQueue plain = gateway.createQueue("default", DESTINATION, 1024);

        DispatcherQueueDeleteResult result = gateway.deleteQueue("market", DESTINATION, false);

        assertThat(result.status()).isEqualTo(DispatcherQueueDeleteResult.Status.DELETED);
        assertThat(registry.get("market", DESTINATION)).isNull();
        assertThat(registry.get(DESTINATION)).isSameAs(plain);
        assertThat(plain.isClosed()).isFalse();
    }

    @Test
    void a_delete_without_a_group_does_not_reach_another_group() {
        DispatcherQueue market = gateway.createQueue("market", DESTINATION, 1024);

        DispatcherQueueDeleteResult result = gateway.deleteQueue("default", DESTINATION, false);

        assertThat(result.status()).isEqualTo(DispatcherQueueDeleteResult.Status.NOT_FOUND);
        assertThat(registry.get("market", DESTINATION)).isSameAs(market);
    }

    @Test
    void a_non_empty_group_queue_is_kept_until_the_delete_is_forced() {
        DispatcherQueue market = gateway.createQueue("market", DESTINATION, 4096);
        market.enqueue(message("market"));

        assertThat(gateway.deleteQueue("market", DESTINATION, false).status())
                .isEqualTo(DispatcherQueueDeleteResult.Status.NOT_EMPTY);
        assertThat(registry.get("market", DESTINATION)).isSameAs(market);

        assertThat(gateway.deleteQueue("market", DESTINATION, true).status())
                .isEqualTo(DispatcherQueueDeleteResult.Status.DELETED);
        assertThat(registry.get("market", DESTINATION)).isNull();
    }

    @Test
    void a_change_on_an_unknown_group_reports_missing_without_creating_it() {
        assertThat(gateway.pauseQueue("unused", DESTINATION)).isFalse();
        assertThat(gateway.resumeQueue("unused", DESTINATION)).isFalse();
        assertThat(gateway.purgeQueue("unused", DESTINATION)).isFalse();
        assertThat(gateway.deleteQueue("unused", DESTINATION, true).status())
                .isEqualTo(DispatcherQueueDeleteResult.Status.NOT_FOUND);

        assertThat(registry.containsGroup("unused")).isFalse();
    }

    @Test
    void a_malformed_group_is_refused_on_creation() {
        assertThatThrownBy(() -> gateway.createQueue("bad/name", DESTINATION, 1024))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid group name");
    }

    @Test
    void a_queue_recreated_after_a_delete_keeps_its_own_consumer() {
        gateway.sparkDispatch(message("market")).join();
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(exported.get("market")).isEqualTo(1));

        assertThat(gateway.deleteQueue("market", DESTINATION, true).status())
                .isEqualTo(DispatcherQueueDeleteResult.Status.DELETED);

        // The replacement registers a consumer of its own. The delete above must not have
        // taken that one down, or nothing would ever drain the new queue.
        gateway.sparkDispatch(message("market")).join();
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(exported.get("market")).isEqualTo(2));
    }

    @Test
    void a_delete_leaves_the_consumer_of_another_group_delivering() {
        gateway.sparkDispatch(message("market")).join();
        gateway.sparkDispatch(message("notification")).join();
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(exported.get("market")).isEqualTo(1);
            assertThat(exported.get("notification")).isEqualTo(1);
        });

        assertThat(gateway.deleteQueue("market", DESTINATION, true).status())
                .isEqualTo(DispatcherQueueDeleteResult.Status.DELETED);

        gateway.sparkDispatch(message("notification")).join();
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(exported.get("notification")).isEqualTo(2));
    }

    @Test
    void a_message_that_arrives_while_a_delete_runs_still_gets_a_consumer() throws Exception {
        CountDownLatch removed = new CountDownLatch(1);
        CountDownLatch resumeDelete = new CountDownLatch(1);
        VirtualThreadExecutorDispatchGateway hooked = new VirtualThreadExecutorDispatchGateway(
                recordingExporter(exported),
                pauseAfterRemove(registry, removed, resumeDelete)
        );

        try {
            hooked.sparkDispatch(message("market")).join();
            await().atMost(Duration.ofSeconds(5))
                    .untilAsserted(() -> assertThat(exported.get("market")).isEqualTo(1));

            CompletableFuture<DispatcherQueueDeleteResult> delete = CompletableFuture.supplyAsync(
                    () -> hooked.deleteQueue("market", DESTINATION, true));
            assertThat(removed.await(5, TimeUnit.SECONDS)).isTrue();

            // The deleted queue is out of the dispatcher and the delete has not finished. This
            // message lands in a queue created on the spot, and no later message will come along
            // to notice it has nothing draining it.
            hooked.sparkDispatch(message("market")).join();
            resumeDelete.countDown();

            assertThat(delete.get(5, TimeUnit.SECONDS).status())
                    .isEqualTo(DispatcherQueueDeleteResult.Status.DELETED);
            await().atMost(Duration.ofSeconds(5))
                    .untilAsserted(() -> assertThat(exported.get("market")).isEqualTo(2));
        } finally {
            hooked.close();
        }
    }

    /** Wraps a dispatcher so a queue removal blocks until the test lets it finish. */
    private static Dispatcher pauseAfterRemove(
            Dispatcher delegate,
            CountDownLatch removed,
            CountDownLatch resume
    ) {
        return (Dispatcher) Proxy.newProxyInstance(
                Dispatcher.class.getClassLoader(),
                new Class<?>[]{Dispatcher.class},
                (proxy, method, arguments) -> {
                    Object result;
                    try {
                        result = method.invoke(delegate, arguments);
                    } catch (InvocationTargetException error) {
                        throw error.getCause();
                    }
                    if (method.getName().equals("remove")
                            && arguments.length == 1
                            && arguments[0] instanceof DispatcherQueue) {
                        removed.countDown();
                        resume.await(5, TimeUnit.SECONDS);
                    }
                    return result;
                }
        );
    }

    private static Message message(String group) {
        return Message.builder()
                .group(group)
                .destination(DESTINATION)
                .createdAt(Instant.now())
                .producerId("test")
                .body("test".getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .build();
    }

    private static DispatchExporter recordingExporter(Map<String, Integer> exported) {
        return new DispatchExporter() {
            @Override
            public String name() {
                return "recording";
            }

            @Override
            public CompletionStage<@Nullable Void> export(String group, Destination destination, Buffer payload) {
                exported.merge(group, 1, Integer::sum);
                return CompletableFuture.completedFuture(null);
            }
        };
    }
}
