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
import java.util.concurrent.TimeUnit;

import org.traffichunter.titan.core.concurrent.Promise;
import org.traffichunter.titan.core.concurrent.ScheduledPromise;
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
 * is reached.</p>
 *
 * @author yungwang-o
 */
public interface EventLoop extends EventLoopLifeCycle {

    /**
     * Starts the event-loop thread.
     */
    void start();

    /**
     * Enqueues a task without wrapping it in a new promise.
     */
    void register(Runnable task);

    /**
     * Enqueues a task and returns a promise completed by that task.
     */
    <V> Promise<V> submit(Runnable task);

    <V> Promise<V> submit(Callable<V> task);

    <V> ScheduledPromise<V> schedule(Runnable task, long delay, TimeUnit unit);

    <V> ScheduledPromise<V> schedule(Callable<V> task, long delay, TimeUnit unit);

    <V> ScheduledPromise<V> scheduleAtFixedRate(Runnable task, long initialDelay, long period, TimeUnit unit);

    <V> ScheduledPromise<V> scheduleWithFixedDelay(Runnable task, long initialDelay, long period, TimeUnit unit);

    default <V> Promise<V> newPromise(Runnable task) {
        return Promise.newPromise(this, task);
    }

    default <V> Promise<V> newPromise(Callable<V> task) {
        return Promise.newPromise(this, task);
    }

    /**
     * Returns whether the current thread is this event loop's owner thread.
     */
    default boolean inEventLoop() {
        return inEventLoop(Thread.currentThread());
    }

    boolean inEventLoop(Thread thread);

    default void gracefullyShutdown() {
        gracefullyShutdown(EventLoopConstants.DEFAULT_SHUTDOWN_TIME_OUT, TimeUnit.SECONDS);
    }

    void gracefullyShutdown(long timeout, TimeUnit unit);

    void close();
}
