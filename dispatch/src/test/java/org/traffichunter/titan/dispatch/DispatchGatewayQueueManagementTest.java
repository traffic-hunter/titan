package org.traffichunter.titan.dispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.traffichunter.titan.core.util.DestinationGroups.DEFAULT;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import org.jspecify.annotations.Nullable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;
import org.traffichunter.titan.core.message.Message;
import org.traffichunter.titan.core.util.Destination;
import org.traffichunter.titan.core.util.DestinationGroups;
import org.traffichunter.titan.core.util.buffer.Buffer;
import org.traffichunter.titan.dispatch.exporter.DispatchExporter;

class DispatchGatewayQueueManagementTest {

    @Test
    void delete_queue_rejects_non_empty_queue_without_force() {
        TrieDispatcher dispatcher = new TrieDispatcher();
        VirtualThreadExecutorDispatchGateway gateway = new VirtualThreadExecutorDispatchGateway(
                noopExporter(),
                dispatcher
        );
        Destination destination = Destination.create("/queue/delete-safe");
        gateway.createQueue(DEFAULT, destination, 10).enqueue(message(destination));

        DispatcherQueueDeleteResult result = gateway.deleteQueue(DEFAULT, destination, false);

        assertThat(result.status()).isEqualTo(DispatcherQueueDeleteResult.Status.NOT_EMPTY);
        assertThat(dispatcher.get(destination)).isNotNull();

        gateway.close();
    }

    @Test
    void delete_queue_with_force_removes_queue_and_allows_auto_create_later() throws Exception {
        TrieDispatcher dispatcher = new TrieDispatcher();
        VirtualThreadExecutorDispatchGateway gateway = new VirtualThreadExecutorDispatchGateway(
                noopExporter(),
                dispatcher
        );
        Destination destination = Destination.create("/queue/delete-force");
        DispatcherQueue first = gateway.createQueue(DEFAULT, destination, 10);
        first.enqueue(message(destination));

        DispatcherQueueDeleteResult result = gateway.deleteQueue(DEFAULT, destination, true);

        assertThat(result.isDeleted()).isTrue();
        assertThat(dispatcher.get(destination)).isNull();

        gateway.sparkDispatch(message(destination)).get();

        assertThat(dispatcher.get(destination)).isNotNull();
        assertThat(dispatcher.get(destination)).isNotSameAs(first);

        gateway.close();
    }

    @Test
    void spark_dispatch_runs_custom_handler_between_route_and_fanout() throws Exception {
        TrieDispatcher dispatcher = new TrieDispatcher();
        VirtualThreadExecutorDispatchGateway gateway = new VirtualThreadExecutorDispatchGateway(
                noopExporter(),
                dispatcher
        );
        AtomicInteger customHandlerCalls = new AtomicInteger();
        gateway.chainHandler(chain -> chain.add((context, chainContext) -> {
            customHandlerCalls.incrementAndGet();
            return chainContext.next(context);
        }));

        gateway.sparkDispatch(message(Destination.create("/queue/dispatch-fanout-chain"))).get();

        assertThat(customHandlerCalls).hasValue(1);

        gateway.close();
    }

    /** Hands every export back as an unfinished future so the test decides when a delivery ends. */
    private static DispatchExporter heldExporter(BlockingQueue<CompletableFuture<@Nullable Void>> exports) {
        return new DispatchExporter() {
            @Override
            public String name() {
                return "held";
            }

            @Override
            public CompletionStage<@Nullable Void> export(String group, Destination destination, Buffer payload) {
                CompletableFuture<@Nullable Void> export = new CompletableFuture<>();
                exports.add(export);
                return export;
            }
        };
    }

    private static DispatchExporter noopExporter() {
        return new DispatchExporter() {
            @Override
            public String name() {
                return "noop";
            }

            @Override
            public CompletionStage<@Nullable Void> export(String group, Destination destination, Buffer payload) {
                return CompletableFuture.completedFuture(null);
            }
        };
    }

    @Test
    void pause_queue_marks_queue_paused() {
        TrieDispatcher dispatcher = new TrieDispatcher();
        VirtualThreadExecutorDispatchGateway gateway = new VirtualThreadExecutorDispatchGateway(
                noopExporter(),
                dispatcher
        );
        Destination destination = Destination.create("/queue/pause-state");
        DispatcherQueue queue = gateway.createQueue(DEFAULT, destination, 1024);

        assertThat(gateway.pauseQueue(DEFAULT, destination)).isTrue();

        assertThat(queue.isPaused()).isTrue();

        gateway.close();
    }

    @Test
    void paused_queue_withholds_queued_messages_from_consumers() throws Exception {
        TrieDispatcher dispatcher = new TrieDispatcher();
        VirtualThreadExecutorDispatchGateway gateway = new VirtualThreadExecutorDispatchGateway(
                noopExporter(),
                dispatcher
        );
        Destination destination = Destination.create("/queue/pause-withholds");
        DispatcherQueue queue = gateway.createQueue(DEFAULT, destination, 1024);
        Message queued = message(destination);
        queue.enqueue(queued);

        assertThat(gateway.pauseQueue(DEFAULT, destination)).isTrue();

        // A manual pause blocks dispatch, so the queued message is withheld
        // until the queue resumes rather than being delivered.
        assertThat(queue.dispatch(200, TimeUnit.MILLISECONDS)).isNull();
        assertThat(queue.size()).isEqualTo(1);

        assertThat(gateway.resumeQueue(DEFAULT, destination)).isTrue();

        assertThat(queue.dispatch(5, TimeUnit.SECONDS)).isSameAs(queued);
        assertThat(queue.size()).isZero();

        gateway.close();
    }

    @Test
    void pressure_paused_queue_still_lets_consumers_drain() throws Exception {
        TrieDispatcher dispatcher = new TrieDispatcher();
        VirtualThreadExecutorDispatchGateway gateway = new VirtualThreadExecutorDispatchGateway(
                noopExporter(),
                dispatcher
        );
        Destination destination = Destination.create("/queue/pressure-drains");
        Message first = message(destination);
        DispatcherQueue queue = gateway.createQueue(DEFAULT, destination, first.getSize());
        queue.enqueue(first);
        queue.enqueue(message(destination));

        assertThat(queue.isPaused()).isTrue();

        // Only a manual pause blocks dispatch. A pressure pause must not, or
        // the queue could never drain back below its resume threshold.
        assertThat(queue.dispatch(5, TimeUnit.SECONDS)).isSameAs(first);

        gateway.close();
    }

    @Test
    void manual_resume_wakes_consumer_parked_while_pressure_pause_is_active() throws Exception {
        TrieDispatcher dispatcher = new TrieDispatcher();
        VirtualThreadExecutorDispatchGateway gateway = new VirtualThreadExecutorDispatchGateway(
                noopExporter(),
                dispatcher
        );
        Destination destination = Destination.create("/queue/manual-resume-under-pressure");
        // Fill the queue exactly so the next message trips the pressure pause,
        // then stack a manual pause on top of it.
        Message first = message(destination);
        DispatcherQueue queue = gateway.createQueue(DEFAULT, destination, first.getSize());
        queue.enqueue(first);
        queue.enqueue(message(destination));
        assertThat(queue.isPaused()).isTrue();
        assertThat(gateway.pauseQueue(DEFAULT, destination)).isTrue();

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch drained = new CountDownLatch(1);
        AtomicReference<Message> received = new AtomicReference<>();
        Thread consumer = new Thread(() -> {
            started.countDown();
            try {
                received.set(queue.dispatch());
                drained.countDown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "manual-resume-consumer-test");
        consumer.setDaemon(true);
        consumer.start();

        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(drained.await(200, TimeUnit.MILLISECONDS)).isFalse();

        gateway.resumeQueue(DEFAULT, destination);

        // Clearing the manual pause has to wake the parked consumer even though
        // the pressure pause is still set. Otherwise nothing drains the queue and
        // the pressure pause can never clear either.
        assertThat(drained.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(received.get()).isSameAs(first);

        consumer.join(TimeUnit.SECONDS.toMillis(5));
        gateway.close();
    }

    @Test
    void consumer_holds_the_message_in_the_queue_until_the_export_completes() throws Exception {
        BlockingQueue<CompletableFuture<@Nullable Void>> exports = new LinkedBlockingQueue<>();
        VirtualThreadExecutorDispatchGateway gateway = new VirtualThreadExecutorDispatchGateway(
                heldExporter(exports),
                new TrieDispatcher()
        );
        Destination destination = Destination.create("/queue/held-until-exported");
        Message first = message(destination);
        Message second = message(destination);
        DispatcherQueue queue = gateway.createQueue(DEFAULT, destination, first.getSize() + second.getSize());

        gateway.sparkDispatch(first).get(5, TimeUnit.SECONDS);
        gateway.sparkDispatch(second).get(5, TimeUnit.SECONDS);

        CompletableFuture<@Nullable Void> firstExport = exports.poll(5, TimeUnit.SECONDS);
        assertThat(firstExport).isNotNull();
        // One in flight, one waiting, both still counted: the queue is full for producers.
        assertThat(exports.poll(200, TimeUnit.MILLISECONDS)).isNull();
        assertThat(queue.size()).isEqualTo(1);
        assertThat(queue.getPendingBytes()).isEqualTo(first.getSize() + second.getSize());
        assertThat(queue.enqueue(message(destination))).isNull();

        firstExport.complete(null);

        CompletableFuture<@Nullable Void> secondExport = exports.poll(5, TimeUnit.SECONDS);
        assertThat(secondExport).isNotNull();
        assertThat(queue.getPendingBytes()).isEqualTo(second.getSize());
        secondExport.complete(null);
        await().atMost(5, TimeUnit.SECONDS).until(() -> queue.getPendingBytes() == 0);
        gateway.close();
    }

    @Test
    void consumer_moves_to_the_next_message_after_an_export_timeout() throws Exception {
        BlockingQueue<CompletableFuture<@Nullable Void>> exports = new LinkedBlockingQueue<>();
        DispatchExporter stalledExporter = new DispatchExporter() {
            @Override
            public String name() {
                return "stalled";
            }

            @Override
            public CompletionStage<@Nullable Void> export(String group, Destination destination, Buffer payload) {
                CompletableFuture<@Nullable Void> export = new CompletableFuture<>();
                exports.add(export);
                return export;
            }
        };
        VirtualThreadExecutorDispatchGateway gateway = new VirtualThreadExecutorDispatchGateway(
                stalledExporter,
                new TrieDispatcher()
        );
        Destination destination = Destination.create("/queue/export-timeout");
        Message first = message(destination);
        DispatcherQueue queue = gateway.createQueue(DEFAULT, destination, first.getSize());

        try {
            gateway.sparkDispatch(first).get(5, TimeUnit.SECONDS);
            CompletableFuture<@Nullable Void> firstExport = exports.poll(5, TimeUnit.SECONDS);
            assertThat(firstExport).isNotNull();

            await().atMost(10, TimeUnit.SECONDS).until(() -> queue.getPendingBytes() == 0);
            assertThat(firstExport).isNotDone();

            gateway.sparkDispatch(message(destination)).get(5, TimeUnit.SECONDS);
            CompletableFuture<@Nullable Void> secondExport = exports.poll(5, TimeUnit.SECONDS);
            assertThat(secondExport).isNotNull();

            firstExport.complete(null);
            secondExport.complete(null);
        } finally {
            gateway.close();
        }
    }

    @Test
    void force_delete_stops_a_consumer_waiting_on_an_export() throws Exception {
        BlockingQueue<CompletableFuture<@Nullable Void>> exports = new LinkedBlockingQueue<>();
        TrieDispatcher dispatcher = new TrieDispatcher();
        VirtualThreadExecutorDispatchGateway gateway = new VirtualThreadExecutorDispatchGateway(
                heldExporter(exports),
                dispatcher
        );
        Destination destination = Destination.create("/queue/force-delete-in-flight");
        DispatcherQueue queue = gateway.createQueue(DEFAULT, destination, 1024);
        Message first = message(destination);

        gateway.sparkDispatch(first).get(5, TimeUnit.SECONDS);
        CompletableFuture<@Nullable Void> firstExport = exports.poll(5, TimeUnit.SECONDS);
        assertThat(firstExport).isNotNull();
        queue.enqueue(message(destination));

        assertThat(gateway.deleteQueue(DEFAULT, destination, true).isDeleted()).isTrue();
        assertThat(dispatcher.get(destination)).isNull();

        // The interrupt wakes the consumer out of get(); it returns the bytes without waiting
        // for the export, and the waiting message went with clear().
        await().atMost(5, TimeUnit.SECONDS).until(() -> queue.getPendingBytes() == 0);

        firstExport.complete(null);

        assertThat(queue.getPendingBytes()).isZero();
        assertThat(exports.poll(200, TimeUnit.MILLISECONDS)).isNull();
        gateway.close();
    }

    @Test
    void paused_queue_holds_producers_until_resumed() throws Exception {
        TrieDispatcher dispatcher = new TrieDispatcher();
        VirtualThreadExecutorDispatchGateway gateway = new VirtualThreadExecutorDispatchGateway(
                noopExporter(),
                dispatcher
        );
        Destination destination = Destination.create("/queue/pause-blocks-producer");
        DispatcherQueue queue = gateway.createQueue(DEFAULT, destination, 1024);
        gateway.pauseQueue(DEFAULT, destination);

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch enqueued = new CountDownLatch(1);
        Thread producer = new Thread(() -> {
            started.countDown();
            queue.enqueue(message(destination));
            enqueued.countDown();
        }, "pause-producer-test");
        producer.setDaemon(true);
        producer.start();

        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(enqueued.await(200, TimeUnit.MILLISECONDS)).isFalse();
        assertThat(queue.size()).isZero();

        gateway.resumeQueue(DEFAULT, destination);

        assertThat(enqueued.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(queue.size()).isEqualTo(1);

        producer.join(TimeUnit.SECONDS.toMillis(5));
        gateway.close();
    }

    @Test
    void pause_queue_is_idempotent() {
        TrieDispatcher dispatcher = new TrieDispatcher();
        VirtualThreadExecutorDispatchGateway gateway = new VirtualThreadExecutorDispatchGateway(
                noopExporter(),
                dispatcher
        );
        Destination destination = Destination.create("/queue/pause-twice");
        DispatcherQueue queue = gateway.createQueue(DEFAULT, destination, 1024);

        assertThat(gateway.pauseQueue(DEFAULT, destination)).isTrue();
        assertThat(gateway.pauseQueue(DEFAULT, destination)).isTrue();

        assertThat(queue.isPaused()).isTrue();

        gateway.close();
    }

    @Test
    void resume_queue_clears_manual_pause() {
        TrieDispatcher dispatcher = new TrieDispatcher();
        VirtualThreadExecutorDispatchGateway gateway = new VirtualThreadExecutorDispatchGateway(
                noopExporter(),
                dispatcher
        );
        Destination destination = Destination.create("/queue/resume-state");
        DispatcherQueue queue = gateway.createQueue(DEFAULT, destination, 1024);
        gateway.pauseQueue(DEFAULT, destination);

        assertThat(gateway.resumeQueue(DEFAULT, destination)).isTrue();

        assertThat(queue.isPaused()).isFalse();

        gateway.close();
    }

    @Test
    void resume_queue_is_idempotent_on_running_queue() {
        TrieDispatcher dispatcher = new TrieDispatcher();
        VirtualThreadExecutorDispatchGateway gateway = new VirtualThreadExecutorDispatchGateway(
                noopExporter(),
                dispatcher
        );
        Destination destination = Destination.create("/queue/resume-running");
        DispatcherQueue queue = gateway.createQueue(DEFAULT, destination, 1024);

        assertThat(gateway.resumeQueue(DEFAULT, destination)).isTrue();

        assertThat(queue.isPaused()).isFalse();

        gateway.close();
    }

    @Test
    void resume_queue_keeps_pressure_pause_while_queue_is_over_limit() {
        TrieDispatcher dispatcher = new TrieDispatcher();
        VirtualThreadExecutorDispatchGateway gateway = new VirtualThreadExecutorDispatchGateway(
                noopExporter(),
                dispatcher
        );
        Destination destination = Destination.create("/queue/resume-under-pressure");
        // Size the queue so the first message fills it exactly. The second
        // message then fails to reserve bytes and trips the pressure pause.
        Message first = message(destination);
        DispatcherQueue queue = gateway.createQueue(DEFAULT, destination, first.getSize());
        queue.enqueue(first);
        queue.enqueue(message(destination));

        assertThat(queue.isPaused()).isTrue();
        assertThat(gateway.resumeQueue(DEFAULT, destination)).isTrue();

        assertThat(queue.isPaused()).isTrue();

        gateway.close();
    }

    @Test
    void purge_queue_keeps_queue_and_empties_pending_messages() {
        TrieDispatcher dispatcher = new TrieDispatcher();
        VirtualThreadExecutorDispatchGateway gateway = new VirtualThreadExecutorDispatchGateway(
                noopExporter(),
                dispatcher
        );
        Destination destination = Destination.create("/queue/purge-state");
        DispatcherQueue queue = gateway.createQueue(DEFAULT, destination, 1024);
        queue.enqueue(message(destination));

        assertThat(gateway.purgeQueue(DEFAULT, destination)).isTrue();

        assertThat(dispatcher.get(destination)).isSameAs(queue);
        assertThat(queue.size()).isZero();
        assertThat(queue.getPendingBytes()).isZero();

        gateway.close();
    }

    @Test
    void queue_state_actions_return_false_for_unknown_queue() {
        TrieDispatcher dispatcher = new TrieDispatcher();
        VirtualThreadExecutorDispatchGateway gateway = new VirtualThreadExecutorDispatchGateway(
                noopExporter(),
                dispatcher
        );
        Destination destination = Destination.create("/queue/missing-state");

        assertThat(gateway.pauseQueue(DEFAULT, destination)).isFalse();
        assertThat(gateway.resumeQueue(DEFAULT, destination)).isFalse();
        assertThat(gateway.purgeQueue(DEFAULT, destination)).isFalse();
        assertThat(dispatcher.get(destination)).isNull();

        gateway.close();
    }

    private static Message message(Destination destination) {
        return Message.builder()
                .destination(destination)
                .createdAt(Instant.now())
                .producerId("test")
                .body("test".getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .build();
    }

    @Test
    void spark_dispatch_with_group_routes_to_group_queue() throws Exception {
        DestinationGroupRegistry registry = new DestinationGroupRegistry();
        Map<String, Integer> exported = new ConcurrentHashMap<>();
        VirtualThreadExecutorDispatchGateway gateway = new VirtualThreadExecutorDispatchGateway(
                recordingExporter(exported),
                registry
        );
        Destination destination = Destination.create("/queue/grouped-route");

        gateway.sparkDispatch(message("market", destination)).get();

        assertThat(registry.get("market", destination)).isNotNull();
        assertThat(registry.get(destination)).isNull();
        awaitExported(exported, "market");
        assertThat(exported).containsOnlyKeys("market");

        gateway.close();
    }

    @Test
    void same_destination_in_two_groups_gets_two_consumers() throws Exception {
        DestinationGroupRegistry registry = new DestinationGroupRegistry();
        Map<String, Integer> exported = new ConcurrentHashMap<>();
        VirtualThreadExecutorDispatchGateway gateway = new VirtualThreadExecutorDispatchGateway(
                recordingExporter(exported),
                registry
        );
        Destination destination = Destination.create("/queue/grouped-twice");

        gateway.sparkDispatch(message("market", destination)).get();
        gateway.sparkDispatch(message("notification", destination)).get();

        awaitExported(exported, "market");
        awaitExported(exported, "notification");
        assertThat(exported).containsOnlyKeys("market", "notification");
        assertThat(registry.get("market", destination)).isNotSameAs(registry.get("notification", destination));

        gateway.close();
    }

    @Test
    void message_without_group_uses_default_group() throws Exception {
        DestinationGroupRegistry registry = new DestinationGroupRegistry();
        Map<String, Integer> exported = new ConcurrentHashMap<>();
        VirtualThreadExecutorDispatchGateway gateway = new VirtualThreadExecutorDispatchGateway(
                recordingExporter(exported),
                registry
        );
        Destination destination = Destination.create("/queue/grouped-default");

        gateway.sparkDispatch(message(destination)).get();

        assertThat(registry.get(destination)).isNotNull();
        awaitExported(exported, DestinationGroups.DEFAULT);
        assertThat(exported).containsOnlyKeys(DestinationGroups.DEFAULT);

        gateway.close();
    }

    private static void awaitExported(Map<String, Integer> exported, String group) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!exported.containsKey(group)) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("No export observed for group " + group + ": " + exported);
            }
            Thread.sleep(10);
        }
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

    private static Message message(String group, Destination destination) {
        return Message.builder()
                .group(group)
                .destination(destination)
                .createdAt(Instant.now())
                .producerId("test")
                .body("test".getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .build();
    }

    @Test
    void deleted_queue_refuses_messages_from_a_producer_holding_it() {
        TrieDispatcher dispatcher = new TrieDispatcher();
        VirtualThreadExecutorDispatchGateway gateway = new VirtualThreadExecutorDispatchGateway(
                noopExporter(),
                dispatcher
        );
        Destination destination = Destination.create("/queue/delete-closes");
        DispatcherQueue queue = gateway.createQueue(DEFAULT, destination, 1024);

        assertThat(gateway.deleteQueue(DEFAULT, destination, false).isDeleted()).isTrue();

        // A producer that resolved the queue before the delete would otherwise write into
        // a queue nothing drains.
        assertThat(queue.isClosed()).isTrue();
        assertThat(queue.enqueue(message(destination))).isNull();
        assertThat(queue.size()).isZero();

        gateway.close();
    }
}
