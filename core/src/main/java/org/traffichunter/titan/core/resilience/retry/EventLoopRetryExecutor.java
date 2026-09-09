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
import org.traffichunter.titan.core.channel.EventLoop;
import org.traffichunter.titan.core.channel.TaskEventLoop;
import org.traffichunter.titan.core.util.concurrent.ScheduledPromise;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Retry executor backed by Titan's {@link EventLoop}.
 *
 * <p>This executor is useful when retry callbacks must run on Titan's event loop
 * instead of a JDK scheduler thread. When constructed without an event loop, it
 * creates and owns a {@link TaskEventLoop}.</p>
 *
 * @author yun
 */
public class EventLoopRetryExecutor implements RetryExecutor {

    private final EventLoop eventLoop;
    private final RetryPolicy retryPolicy;
    private final RetryListener retryListener;

    public EventLoopRetryExecutor(RetryPolicy retryPolicy) {
        this(retryPolicy, RetryListener.NOOP);
    }

    public EventLoopRetryExecutor(RetryPolicy retryPolicy, RetryListener retryListener) {
        this(new TaskEventLoop(), retryPolicy, retryListener);
    }

    public EventLoopRetryExecutor(EventLoop eventLoop, RetryListener retryListener, RetryPolicy retryPolicy) {
        this(eventLoop, retryPolicy, retryListener);
    }

    private EventLoopRetryExecutor(
            EventLoop eventLoop,
            RetryPolicy retryPolicy,
            RetryListener retryListener
    ) {
        this.eventLoop = eventLoop;
        this.retryPolicy = retryPolicy;
        this.retryListener = retryListener;
    }

    @Override
    public RetryResult retry(Runnable callback) {
        EventLoopRetryResult result = new EventLoopRetryResult();
        schedule(() -> {
            callback.run();
            return Boolean.TRUE;
        }, result, 1);
        return result;
    }

    @Override
    public <T> RetryResult retry(Callable<T> callback) {
        EventLoopRetryResult result = new EventLoopRetryResult();
        schedule(callback, result, 1);
        return result;
    }

    @Override
    public void shutdown(long timeout, TimeUnit timeUnit) {
        eventLoop.gracefullyShutdown(timeout, timeUnit);
    }

    private <T> void schedule(Callable<T> callback, EventLoopRetryResult result, int attempt) {
        if (eventLoop.isNotStarted()) {
            eventLoop.start();
        }

        if (result.isCancelled() || !retryPolicy.canRetry(attempt)) {
            return;
        }

        Duration delay = retryPolicy.delay(attempt);
        retryListener.onRetry(attempt, delay);
        ScheduledPromise<?> scheduledPromise = eventLoop.schedule(() ->
                run(callback, result, attempt), delay.toNanos(), TimeUnit.NANOSECONDS);

        result.set(scheduledPromise);
    }

    private <T> void run(Callable<T> callback, EventLoopRetryResult result, int attempt) {
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

    private static final class EventLoopRetryResult implements RetryResult {

        private final AtomicBoolean cancellationRequested = new AtomicBoolean(false);
        private final AtomicReference<@Nullable ScheduledPromise<?>> scheduledPromise = new AtomicReference<>();

        @Override
        public void cancel(boolean mayInterruptIfRunning) {
            cancellationRequested.set(true);
            ScheduledPromise<?> promise = scheduledPromise.get();
            if (promise == null) {
                return;
            }

            promise.cancel(mayInterruptIfRunning);
        }

        @Override
        public boolean isCancelled() {
            ScheduledPromise<?> promise = scheduledPromise.get();
            return cancellationRequested.get() || promise != null && promise.isCancelled();
        }

        private void set(ScheduledPromise<?> promise) {
            if (!cancellationRequested.get()) {
                scheduledPromise.set(promise);
                return;
            }

            promise.cancel(false);
        }
    }
}
