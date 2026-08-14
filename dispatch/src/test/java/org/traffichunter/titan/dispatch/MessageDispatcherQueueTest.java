package org.traffichunter.titan.dispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.traffichunter.titan.core.message.Message;
import org.traffichunter.titan.core.util.Destination;
import org.traffichunter.titan.core.util.buffer.Buffer;

class MessageDispatcherQueueTest {

    @Test
    void enqueue_returns_null_when_interrupted_while_paused() throws Exception {
        MessageDispatcherQueue queue = new MessageDispatcherQueue(Destination.create("/queue/paused"), 10);
        CountDownLatch started = new CountDownLatch(1);
        AtomicReference<Message> result = new AtomicReference<>();
        AtomicBoolean interrupted = new AtomicBoolean();

        queue.pause();

        assertThat(queue.metadata().isPaused()).isTrue();

        Thread producer = new Thread(() -> {
            started.countDown();
            result.set(queue.enqueue(message("/queue/paused")));
            interrupted.set(Thread.currentThread().isInterrupted());
        });

        producer.start();
        assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();

        producer.interrupt();
        producer.join(TimeUnit.SECONDS.toMillis(1));

        assertThat(producer.isAlive()).isFalse();
        assertThat(result.get()).isNull();
        assertThat(interrupted).isTrue();
        assertThat(queue.size()).isZero();
    }

    @Test
    void metadata_tracks_pending_bytes_across_queue_lifecycle() throws Exception {
        Destination destination = Destination.create("/queue/metadata");
        DestinationQueueMetadata metadata = new DestinationQueueMetadata(
                destination.path(),
                Instant.now(),
                8
        );
        MessageDispatcherQueue queue = new MessageDispatcherQueue(destination, metadata);
        Message first = message("/queue/metadata");
        Message second = message("/queue/metadata");
        Message rejected = message("/queue/metadata");

        assertThat(queue.enqueue(first)).isSameAs(first);
        assertThat(queue.enqueue(second)).isSameAs(second);
        assertThat(queue.enqueue(rejected)).isNull();
        assertThat(metadata.getPendingBytes()).isEqualTo(8);
        assertThat(metadata.isSaturated()).isTrue();

        assertThat(queue.dispatch(1, TimeUnit.SECONDS)).isSameAs(first);
        assertThat(metadata.getPendingBytes()).isEqualTo(4);
        assertThat(metadata.isSaturated()).isFalse();

        queue.clear();

        assertThat(metadata.getPendingBytes()).isZero();
    }

    @Test
    void concurrent_reservations_do_not_overshoot_pending_byte_limit() throws Exception {
        DestinationQueueMetadata metadata = new DestinationQueueMetadata(
                "/queue/concurrent-metadata",
                Instant.now(),
                40
        );
        CountDownLatch start = new CountDownLatch(1);
        ConcurrentLinkedQueue<Message> accepted = new ConcurrentLinkedQueue<>();
        List<Thread> publishers = java.util.stream.IntStream.range(0, 32)
                .mapToObj(index -> Thread.ofPlatform().unstarted(() -> {
                    try {
                        start.await();
                        Message message = message("/queue/concurrent-metadata");
                        if (metadata.tryReserve(message.getSize())) {
                            accepted.add(message);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }))
                .toList();

        publishers.forEach(Thread::start);
        start.countDown();
        for (Thread publisher : publishers) {
            publisher.join(TimeUnit.SECONDS.toMillis(1));
            assertThat(publisher.isAlive()).isFalse();
        }

        assertThat(accepted).hasSize(10);
        assertThat(metadata.getPendingBytes()).isEqualTo(40);
    }

    @Test
    void metadata_rejects_negative_release_bytes() {
        DestinationQueueMetadata metadata = new DestinationQueueMetadata(
                "/queue/release-validation",
                Instant.now(),
                10
        );

        assertThatThrownBy(() -> metadata.release(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bytes must not be negative");
    }

    @Test
    void queue_resumes_after_pending_bytes_are_released() throws Exception {
        Destination destination = Destination.create("/queue/pressure-resume");
        DestinationQueueMetadata metadata = new DestinationQueueMetadata(
                destination.path(),
                Instant.now(),
                4
        );
        MessageDispatcherQueue queue = new MessageDispatcherQueue(destination, metadata);
        Message accepted = message("/queue/pressure-resume");
        Message rejected = message("/queue/pressure-resume");

        assertThat(queue.enqueue(accepted)).isSameAs(accepted);
        assertThat(queue.enqueue(rejected)).isNull();
        assertThat(queue.isPaused()).isTrue();
        assertThat(metadata.isPaused()).isTrue();

        assertThat(queue.dispatch(1, TimeUnit.SECONDS)).isSameAs(accepted);

        assertThat(queue.isPaused()).isFalse();
        assertThat(metadata.isPaused()).isFalse();
        assertThat(queue.enqueue(rejected)).isSameAs(rejected);
    }

    @Test
    void queue_remains_paused_until_pending_bytes_reach_resume_threshold() throws Exception {
        Destination destination = Destination.create("/queue/low-watermark");
        DestinationQueueMetadata metadata = new DestinationQueueMetadata(
                destination.path(),
                Instant.now(),
                12,
                6
        );
        MessageDispatcherQueue queue = new MessageDispatcherQueue(destination, metadata);
        Message first = message("/queue/low-watermark");
        Message second = message("/queue/low-watermark");
        Message third = message("/queue/low-watermark");

        assertThat(queue.enqueue(first)).isSameAs(first);
        assertThat(queue.enqueue(second)).isSameAs(second);
        assertThat(queue.enqueue(third)).isSameAs(third);
        assertThat(queue.enqueue(message("/queue/low-watermark"))).isNull();
        assertThat(queue.isPaused()).isTrue();

        assertThat(queue.dispatch()).isSameAs(first);
        assertThat(metadata.getPendingBytes()).isEqualTo(8);
        assertThat(queue.isPaused()).isTrue();

        assertThat(queue.dispatch()).isSameAs(second);
        assertThat(metadata.getPendingBytes()).isEqualTo(4);
        assertThat(queue.isPaused()).isFalse();
    }

    @Test
    void oversized_message_is_rejected_without_pausing_queue() {
        Destination destination = Destination.create("/queue/oversized");
        DestinationQueueMetadata metadata = new DestinationQueueMetadata(
                destination.path(),
                Instant.now(),
                3
        );
        MessageDispatcherQueue queue = new MessageDispatcherQueue(destination, metadata);

        assertThat(queue.enqueue(message("/queue/oversized"))).isNull();
        assertThat(queue.isPaused()).isFalse();
        assertThat(metadata.isPaused()).isFalse();
    }

    @Test
    void automatic_resume_does_not_override_manual_pause() throws Exception {
        Destination destination = Destination.create("/queue/manual-pause");
        DestinationQueueMetadata metadata = new DestinationQueueMetadata(
                destination.path(),
                Instant.now(),
                4
        );
        MessageDispatcherQueue queue = new MessageDispatcherQueue(destination, metadata);
        Message accepted = message("/queue/manual-pause");

        assertThat(queue.enqueue(accepted)).isSameAs(accepted);
        assertThat(queue.enqueue(message("/queue/manual-pause"))).isNull();
        queue.pause();

        assertThat(queue.dispatch(1, TimeUnit.SECONDS)).isSameAs(accepted);
        assertThat(queue.isPaused()).isTrue();
        assertThat(metadata.isPaused()).isTrue();

        queue.resume();

        assertThat(queue.isPaused()).isFalse();
        assertThat(metadata.isPaused()).isFalse();
    }

    @Test
    void clear_empty_queue_does_not_release_bytes() {
        MessageDispatcherQueue queue = new MessageDispatcherQueue(
                Destination.create("/queue/empty-clear"),
                10
        );

        queue.clear();

        assertThat(queue.size()).isZero();
        assertThat(queue.metadata().getPendingBytes()).isZero();
    }

    @Test
    void snapshot_returns_snapshot_without_draining_queue() throws Exception {
        MessageDispatcherQueue queue = new MessageDispatcherQueue(Destination.create("/queue/pressure"), 10);
        Message first = message("/queue/pressure");
        Message second = message("/queue/pressure");

        queue.enqueue(first);
        queue.enqueue(second);

        List<Message> pressure = queue.snapshot();

        assertThat(pressure).containsExactly(first, second);
        assertThat(queue.size()).isEqualTo(2);
        assertThat(queue.dispatch(1, TimeUnit.SECONDS)).isSameAs(first);
        assertThat(queue.dispatch(1, TimeUnit.SECONDS)).isSameAs(second);
    }

    private static Message message(String destination) {
        return Message.builder()
                .destination(Destination.create(destination))
                .createdAt(Instant.now())
                .producerId("test")
                .body("test".getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .build();
    }
}
