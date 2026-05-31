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

import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.channel.EventLoop;
import org.traffichunter.titan.core.channel.TaskEventLoop;
import org.traffichunter.titan.core.concurrent.ScheduledPromise;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
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
        schedule(Executors.callable(callback), result, 1);
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
        retryListener.onRetryScheduled(attempt, delay);
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
            if (!retryPolicy.canRetry(attempt + 1)) {
                retryListener.onRetryExhausted(attempt, e);
                return;
            }
            schedule(callback, result, attempt + 1);
        }
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
