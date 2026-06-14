package org.traffichunter.titan.core.test.implementation;

import io.vertx.core.Vertx;
import org.junit.jupiter.api.Test;
import org.traffichunter.titan.core.resilience.retry.RetryExecutor;
import org.traffichunter.titan.core.resilience.retry.RetryPolicy;
import org.traffichunter.titan.core.resilience.retry.RetryResult;
import org.traffichunter.titan.core.resilience.retry.VertxRetryExecutor;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class VertxRetryExecutorTest {

    @Test
    void retries_blocking_callback_on_worker_thread_until_success() throws Exception {
        Vertx vertx = Vertx.vertx();
        RetryExecutor executor = new VertxRetryExecutor(
                vertx,
                RetryPolicy.fixed(3, Duration.ofMillis(10))
        );
        AtomicInteger attempts = new AtomicInteger();
        AtomicReference<String> threadName = new AtomicReference<>();
        CountDownLatch completed = new CountDownLatch(1);

        try {
            executor.retry(() -> {
                threadName.set(Thread.currentThread().getName());
                if (attempts.incrementAndGet() < 3) {
                    throw new IllegalStateException("not yet");
                }
                completed.countDown();
            });

            assertThat(completed.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(attempts).hasValue(3);
            assertThat(threadName.get()).contains("vert.x-worker-thread");
        } finally {
            vertx.close().await(3, TimeUnit.SECONDS);
        }
    }

    @Test
    void cancellation_prevents_scheduled_attempt() throws Exception {
        Vertx vertx = Vertx.vertx();
        RetryExecutor executor = new VertxRetryExecutor(
                vertx,
                RetryPolicy.fixed(RetryPolicy.UNLIMITED_ATTEMPTS, Duration.ofMillis(200))
        );
        AtomicInteger attempts = new AtomicInteger();

        try {
            RetryResult result = executor.retry(attempts::incrementAndGet);
            result.cancel();

            Thread.sleep(300);

            assertThat(result.isCancelled()).isTrue();
            assertThat(attempts).hasValue(0);
        } finally {
            vertx.close().await(3, TimeUnit.SECONDS);
        }
    }

    @Test
    void shutdown_does_not_close_injected_vertx() {
        Vertx vertx = Vertx.vertx();
        RetryExecutor executor = new VertxRetryExecutor(
                vertx,
                RetryPolicy.fixed(1, Duration.ofMillis(10))
        );

        executor.shutdown(1, TimeUnit.SECONDS);

        try {
            assertThat(vertx.setTimer(1, ignored -> { })).isNotNegative();
        } finally {
            vertx.close().await();
        }
    }
}
