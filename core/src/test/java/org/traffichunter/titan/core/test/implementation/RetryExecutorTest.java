package org.traffichunter.titan.core.test.implementation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.awaitility.Awaitility;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.traffichunter.titan.core.channel.EventLoop;
import org.traffichunter.titan.core.channel.TaskEventLoop;
import org.traffichunter.titan.core.resilience.retry.EventLoopRetryExecutor;
import org.traffichunter.titan.core.resilience.retry.FixedRetryPolicy;
import org.traffichunter.titan.core.resilience.retry.JdkScheduledRetryExecutor;
import org.traffichunter.titan.core.resilience.retry.RetryListener;
import org.traffichunter.titan.core.resilience.retry.RetryPolicy;
import org.traffichunter.titan.core.resilience.retry.RetryResult;

class RetryExecutorTest {

    private final EventLoop eventLoop = new TaskEventLoop();

    @AfterEach
    void tearDown() {
        if (eventLoop.isStarted()) {
            eventLoop.gracefullyShutdown(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void jdk_executor_retries_until_callback_succeeds() {
        JdkScheduledRetryExecutor executor = new JdkScheduledRetryExecutor(shortRetryPolicy());
        AtomicInteger attempts = new AtomicInteger();
        CountDownLatch success = new CountDownLatch(1);

        executor.retry(() -> {
            if (attempts.incrementAndGet() < 3) {
                throw new IllegalStateException("try again");
            }
            success.countDown();
        });

        Awaitility.await().atMost(1, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(success.getCount()).isZero();
            assertThat(attempts).hasValue(3);
        });
        executor.shutdown();
    }

    @Test
    void jdk_executor_stops_when_attempts_are_exhausted() {
        RecordingRetryListener listener = new RecordingRetryListener();
        JdkScheduledRetryExecutor executor = new JdkScheduledRetryExecutor(
                java.util.concurrent.Executors.newSingleThreadScheduledExecutor(),
                shortRetryPolicy(),
                listener
        );
        AtomicInteger attempts = new AtomicInteger();

        executor.retry(() -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("always fails");
        });

        Awaitility.await().atMost(1, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(attempts).hasValue(3));
        assertThat(listener.events()).containsExactly(
                "scheduled:1",
                "failed:1",
                "scheduled:2",
                "failed:2",
                "scheduled:3",
                "failed:3",
                "exhausted:3"
        );
        executor.shutdown();
    }

    @Test
    void jdk_executor_cancels_scheduled_retry() {
        JdkScheduledRetryExecutor executor = new JdkScheduledRetryExecutor(
                RetryPolicy.fixed(1, Duration.ofMillis(200))
        );
        AtomicInteger attempts = new AtomicInteger();

        RetryResult result = executor.retry(attempts::incrementAndGet);
        result.cancel();

        Awaitility.await().during(250, TimeUnit.MILLISECONDS)
                .atMost(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertThat(attempts).hasValue(0));
        assertThat(result.isCancelled()).isTrue();
        executor.shutdown();
    }

    @Test
    void jdk_executor_reports_cancelled_even_before_schedule_reference_is_visible() {
        JdkScheduledRetryExecutor executor = new JdkScheduledRetryExecutor(
                RetryPolicy.fixed(1, Duration.ofMillis(200))
        );

        RetryResult result = executor.retry(() -> { });
        result.cancel();

        assertThat(result.isCancelled()).isTrue();
        executor.shutdown();
    }

    @Test
    void event_loop_executor_retries_until_callback_succeeds() {
        eventLoop.start();
        RecordingRetryListener listener = new RecordingRetryListener();
        EventLoopRetryExecutor executor = new EventLoopRetryExecutor(eventLoop, listener, shortRetryPolicy());
        AtomicInteger attempts = new AtomicInteger();
        CountDownLatch success = new CountDownLatch(1);

        executor.retry(() -> {
            if (attempts.incrementAndGet() < 3) {
                throw new IllegalStateException("try again");
            }
            success.countDown();
        });

        Awaitility.await().atMost(1, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(success.getCount()).isZero();
            assertThat(attempts).hasValue(3);
        });
        assertThat(listener.events()).containsExactly(
                "scheduled:1",
                "failed:1",
                "scheduled:2",
                "failed:2",
                "scheduled:3"
        );
    }

    @Test
    void event_loop_executor_cancels_scheduled_retry() {
        eventLoop.start();
        EventLoopRetryExecutor executor = new EventLoopRetryExecutor(
                eventLoop,
                RetryListener.NOOP,
                RetryPolicy.fixed(1, Duration.ofMillis(200))
        );
        AtomicInteger attempts = new AtomicInteger();

        RetryResult result = executor.retry(attempts::incrementAndGet);
        result.cancel();

        Awaitility.await().during(250, TimeUnit.MILLISECONDS)
                .atMost(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertThat(attempts).hasValue(0));
        assertThat(result.isCancelled()).isTrue();
    }

    @Test
    void event_loop_executor_reports_cancelled_even_before_schedule_reference_is_visible() {
        eventLoop.start();
        EventLoopRetryExecutor executor = new EventLoopRetryExecutor(
                eventLoop,
                RetryListener.NOOP,
                RetryPolicy.fixed(1, Duration.ofMillis(200))
        );

        RetryResult result = executor.retry(() -> { });
        result.cancel();

        assertThat(result.isCancelled()).isTrue();
    }

    private static FixedRetryPolicy shortRetryPolicy() {
        return FixedRetryPolicy.builder()
                .maxAttempts(3)
                .delay(Duration.ofMillis(10))
                .build();
    }

    @NullMarked
    private static final class RecordingRetryListener implements RetryListener {

        private final List<String> events = java.util.Collections.synchronizedList(new ArrayList<>());

        @Override
        public void onRetryScheduled(int attempt, Duration delay) {
            events.add("scheduled:" + attempt);
        }

        @Override
        public void onRetryFailed(int attempt, Throwable cause) {
            events.add("failed:" + attempt);
        }

        @Override
        public void onRetryExhausted(int attempt, Throwable cause) {
            events.add("exhausted:" + attempt);
        }

        private List<String> events() {
            return List.copyOf(events);
        }
    }
}
