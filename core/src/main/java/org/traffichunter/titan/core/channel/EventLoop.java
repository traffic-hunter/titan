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
package org.traffichunter.titan.core.channel;

import java.util.concurrent.Callable;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.traffichunter.titan.core.util.concurrent.Promise;
import org.traffichunter.titan.core.util.concurrent.ScheduledPromise;
import org.traffichunter.titan.core.util.concurrent.EventExecutorService;

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
     * Starts an orderly shutdown using the default timeout.
     *
     * <p>Tasks accepted before this call are processed until the timeout expires. New tasks
     * are rejected as soon as shutdown begins.</p>
     */
    default void gracefullyShutdown() {
        gracefullyShutdown(EventLoopConstants.DEFAULT_SHUTDOWN_TIME_OUT, TimeUnit.SECONDS);
    }

    /**
     * Starts an orderly shutdown and gives accepted tasks up to the given timeout to finish.
     */
    void gracefullyShutdown(long timeout, TimeUnit unit);

    /**
     * Starts an orderly shutdown without imposing a Titan shutdown timeout.
     */
    @Override
    void shutdown();

    @Override
    List<Runnable> shutdownNow();

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
