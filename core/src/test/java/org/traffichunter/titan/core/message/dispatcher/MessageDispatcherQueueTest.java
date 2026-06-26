package org.traffichunter.titan.core.message.dispatcher;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
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
                .body(Buffer.alloc("test".getBytes()))
                .build();
    }
}
