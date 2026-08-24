/*
 * The MIT License
 *
 * Copyright (c) 2025 traffic-hunter
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.traffichunter.titan.core.channel;

import java.util.concurrent.Callable;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.traffichunter.titan.core.util.concurrent.Promise;
import org.traffichunter.titan.core.util.concurrent.ScheduledPromise;
import org.traffichunter.titan.core.util.concurrent.EventExecutorService;
import org.traffichunter.titan.core.util.event.EventLoopConstants;

/**
 * Single execution lane for asynchronous work.
 *
 * <p>An event loop owns a task queue, scheduled tasks, and optionally I/O selector work.
 * Channel code relies on {@link #inEventLoop()} to preserve thread affinity: channel state,
 * selector registrations, and promise listeners should run on the owning loop.</p>
 *
 * <p>Submitting a task returns a {@link Promise}. Scheduling returns a
 * {@link ScheduledPromise} that is executed by the same event-loop thread when its deadline
 * is reached. Tasks submitted to an event loop should not run blocking code.</p>
 *
 * @author yungwang-o
 */
public interface EventLoop extends EventExecutorService {

    /**
     * Returns whether this event loop has not started yet.
     */
    boolean isNotStarted();

    /**
     * Returns whether this event loop is accepting and processing work.
     */
    boolean isStarted();

    /**
     * Returns whether graceful or immediate shutdown has begun.
     */
    boolean isShuttingDown();

    /**
     * Starts the event-loop thread.
     */
    void start();

    /**
     * Schedules a task to run once after the given delay.
     *
     * <p>Do not run blocking code in the scheduled task.</p>
     */
    @Override
    ScheduledPromise<?> schedule(Runnable task, long delay, TimeUnit unit);

    /**
     * Schedules a callable task to run once after the given delay.
     *
     * <p>Do not run blocking code in the scheduled task.</p>
     */
    @Override
    <V> ScheduledPromise<V> schedule(Callable<V> task, long delay, TimeUnit unit);

    /**
     * Schedules a task to run repeatedly at a fixed rate.
     *
     * <p>Do not run blocking code in the scheduled task.</p>
     */
    @Override
    ScheduledPromise<?> scheduleAtFixedRate(Runnable task, long initialDelay, long period, TimeUnit unit);

    /**
     * Schedules a task to run repeatedly with a fixed delay between runs.
     *
     * <p>Do not run blocking code in the scheduled task.</p>
     */
    @Override
    ScheduledPromise<?> scheduleWithFixedDelay(Runnable task, long initialDelay, long period, TimeUnit unit);

    /**
     * Shuts down the event loop using the default timeout.
     */
    default void gracefullyShutdown() {
        gracefullyShutdown(EventLoopConstants.DEFAULT_SHUTDOWN_TIME_OUT, TimeUnit.SECONDS);
    }

    /**
     * Shuts down the event loop after waiting up to the given timeout.
     */
    void gracefullyShutdown(long timeout, TimeUnit unit);

    @Override
    default void shutdown() {
        gracefullyShutdown();
    }

    @Override
    default List<Runnable> shutdownNow() {
        gracefullyShutdown(0, TimeUnit.NANOSECONDS);
        return List.of();
    }

    @Override
    default boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        while (!isTerminated()) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                return false;
            }
            TimeUnit.NANOSECONDS.sleep(Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(10)));
        }
        return true;
    }

    void close();
}
