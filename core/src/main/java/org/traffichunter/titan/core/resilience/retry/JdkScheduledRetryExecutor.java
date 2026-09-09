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
package org.traffichunter.titan.core.resilience.retry;

import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Retry executor backed by a JDK {@link ScheduledExecutorService}.
 *
 * <p>This implementation is suitable for integration layers that do not need to
 * run retry callbacks on Titan's event loop. When constructed without an executor,
 * it creates and owns a single-threaded scheduled executor.</p>
 *
 * @author yun
 */
public class JdkScheduledRetryExecutor implements RetryExecutor {

    private final ScheduledExecutorService executor;
    private final RetryPolicy retryPolicy;
    private final RetryListener retryListener;

    public JdkScheduledRetryExecutor(RetryPolicy retryPolicy) {
        this(new ScheduledThreadPoolExecutor(1), retryPolicy, RetryListener.NOOP);
    }

    public JdkScheduledRetryExecutor(RetryPolicy retryPolicy, RetryListener retryListener) {
        this(new ScheduledThreadPoolExecutor(1), retryPolicy, retryListener);
    }

    public JdkScheduledRetryExecutor(ScheduledExecutorService executor, RetryPolicy retryPolicy) {
        this(executor, retryPolicy, RetryListener.NOOP);
    }

    public JdkScheduledRetryExecutor(
            ScheduledExecutorService executor,
            RetryPolicy retryPolicy,
            RetryListener retryListener
    ) {
        this.executor = executor;
        this.retryPolicy = retryPolicy;
        this.retryListener = retryListener;
    }

    @Override
    public RetryResult retry(Runnable callback) {
        JdkRetryResult result = new JdkRetryResult();
        schedule(Executors.callable(callback), result, 1);
        return result;
    }

    @Override
    public <T> RetryResult retry(Callable<T> callback) {
        JdkRetryResult result = new JdkRetryResult();
        schedule(callback, result, 1);
        return result;
    }

    @Override
    public void shutdown(long timeout, TimeUnit timeUnit) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(timeout, timeUnit)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    private <T> void schedule(Callable<T> callback, JdkRetryResult result, int attempt) {
        if (result.isCancelled() || !retryPolicy.canRetry(attempt)) {
            return;
        }

        Duration delay = retryPolicy.delay(attempt);
        retryListener.onRetry(attempt, delay);
        ScheduledFuture<?> future = executor.schedule(() -> run(callback, result, attempt), delay.toNanos(), TimeUnit.NANOSECONDS);
        result.set(future);
    }

    private <T> void run(Callable<T> callback, JdkRetryResult result, int attempt) {
        if (result.isCancelled()) {
            return;
        }

        try {
            callback.call();
        } catch (Exception e) {
            retryListener.onRetryFailed(attempt, e);
            schedule(callback, result, nextAttempt(attempt));
        }
    }

    private static int nextAttempt(int attempt) {
        return attempt == Integer.MAX_VALUE ? Integer.MAX_VALUE : attempt + 1;
    }

    private static final class JdkRetryResult implements RetryResult {

        private final AtomicBoolean cancellationRequested = new AtomicBoolean(false);
        private final AtomicReference<@Nullable ScheduledFuture<?>> future = new AtomicReference<>();

        @Override
        public void cancel(boolean mayInterruptIfRunning) {
            cancellationRequested.set(true);
            ScheduledFuture<?> scheduledFuture = future.get();
            if (scheduledFuture == null) {
                return;
            }

            scheduledFuture.cancel(mayInterruptIfRunning);
        }

        @Override
        public boolean isCancelled() {
            ScheduledFuture<?> scheduledFuture = future.get();
            return cancellationRequested.get() || scheduledFuture != null && scheduledFuture.isCancelled();
        }

        private void set(ScheduledFuture<?> scheduledFuture) {
            if (!cancellationRequested.get()) {
                future.set(scheduledFuture);
                return;
            }
            scheduledFuture.cancel(false);
        }
    }
}
