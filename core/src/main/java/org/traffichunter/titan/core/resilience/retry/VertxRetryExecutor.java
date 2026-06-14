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
package org.traffichunter.titan.core.resilience.retry;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Retry executor backed by Vert.x timers and worker threads.
 *
 * <p>Retry delays are scheduled on Vert.x, while callbacks run through
 * {@link Vertx#executeBlocking(Callable)} so blocking callbacks do not block
 * a Vert.x event-loop thread. A Vert.x instance created by this executor is
 * closed by {@link #shutdown(long, TimeUnit)}. An injected instance remains
 * owned by the caller.</p>
 *
 * @author yun
 */
public final class VertxRetryExecutor implements RetryExecutor {

    private final Vertx vertx;
    private final RetryPolicy retryPolicy;
    private final RetryListener retryListener;
    private final boolean managedVertx;

    public VertxRetryExecutor(RetryPolicy retryPolicy) {
        this(retryPolicy, RetryListener.NOOP);
    }

    public VertxRetryExecutor(RetryPolicy retryPolicy, RetryListener retryListener) {
        this(Vertx.vertx(), retryPolicy, retryListener, true);
    }

    public VertxRetryExecutor(VertxOptions options, RetryPolicy retryPolicy) {
        this(options, retryPolicy, RetryListener.NOOP);
    }

    public VertxRetryExecutor(VertxOptions options, RetryPolicy retryPolicy, RetryListener retryListener) {
        this(Vertx.vertx(options), retryPolicy, retryListener, true);
    }

    public VertxRetryExecutor(Vertx vertx, RetryPolicy retryPolicy) {
        this(vertx, retryPolicy, RetryListener.NOOP);
    }

    public VertxRetryExecutor(Vertx vertx, RetryPolicy retryPolicy, RetryListener retryListener) {
        this(vertx, retryPolicy, retryListener, false);
    }

    private VertxRetryExecutor(
            Vertx vertx,
            RetryPolicy retryPolicy,
            RetryListener retryListener,
            boolean managedVertx
    ) {
        this.vertx = vertx;
        this.retryPolicy = retryPolicy;
        this.retryListener = retryListener;
        this.managedVertx = managedVertx;
    }

    @Override
    public RetryResult retry(Runnable callback) {
        return retry(() -> {
            callback.run();
            return Boolean.TRUE;
        });
    }

    @Override
    public <T> RetryResult retry(Callable<T> callback) {
        VertxRetryResult result = new VertxRetryResult(vertx);
        schedule(callback, result, 1);
        return result;
    }

    @Override
    public void shutdown(long timeout, TimeUnit timeUnit) {
        if (!managedVertx) {
            return;
        }

        try {
            vertx.close().await(timeout, timeUnit);
        } catch (TimeoutException e) {
            throw new IllegalStateException("Timed out shutting down Vert.x retry executor", e);
        }
    }

    private <T> void schedule(Callable<T> callback, VertxRetryResult result, int attempt) {
        if (result.isCancelled() || !retryPolicy.canRetry(attempt)) {
            return;
        }

        Duration delay = retryPolicy.delay(attempt);
        retryListener.onRetry(attempt, delay);
        long timerId = vertx.setTimer(Math.max(1, delay.toMillis()), ignored -> {
            result.timerFired();
            if (result.isCancelled()) {
                return;
            }

            vertx.executeBlocking(callback).onFailure(error -> {
                if (result.isCancelled()) {
                    return;
                }
                retryListener.onRetryFailed(attempt, error);
                schedule(callback, result, nextAttempt(attempt));
            });
        });
        result.setTimer(timerId);
    }

    private static int nextAttempt(int attempt) {
        return attempt == Integer.MAX_VALUE ? Integer.MAX_VALUE : attempt + 1;
    }

    private static final class VertxRetryResult implements RetryResult {

        private static final long NO_TIMER = -1;

        private final Vertx vertx;
        private final AtomicBoolean cancellationRequested = new AtomicBoolean(false);
        private final AtomicLong timerId = new AtomicLong(NO_TIMER);

        private VertxRetryResult(Vertx vertx) {
            this.vertx = vertx;
        }

        @Override
        public void cancel(boolean mayInterruptIfRunning) {
            cancellationRequested.set(true);
            cancelTimer(timerId.getAndSet(NO_TIMER));
        }

        @Override
        public boolean isCancelled() {
            return cancellationRequested.get();
        }

        private void setTimer(long timerId) {
            if (cancellationRequested.get()) {
                cancelTimer(timerId);
                return;
            }

            long previous = this.timerId.getAndSet(timerId);
            cancelTimer(previous);

            if (cancellationRequested.get() && this.timerId.compareAndSet(timerId, NO_TIMER)) {
                cancelTimer(timerId);
            }
        }

        private void timerFired() {
            timerId.set(NO_TIMER);
        }

        private void cancelTimer(long timerId) {
            if (timerId != NO_TIMER) {
                vertx.cancelTimer(timerId);
            }
        }
    }
}
