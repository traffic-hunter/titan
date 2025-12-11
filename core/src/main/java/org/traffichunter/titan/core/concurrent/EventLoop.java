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
package org.traffichunter.titan.core.concurrent;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import org.traffichunter.titan.core.util.concurrent.Promise;
import org.traffichunter.titan.core.util.concurrent.ScheduledPromise;
import org.traffichunter.titan.core.util.event.EventLoopConstants;

/**
 * @author yungwang-o
 */
public interface EventLoop extends EventLoopLifeCycle {

    void start();

    void register(Runnable task);

    <V> Promise<V> submit(Runnable task);

    <V> Promise<V> submit(Callable<V> task);

    <V> ScheduledPromise<V> schedule(Runnable task, long delay, TimeUnit unit);

    <V> ScheduledPromise<V> schedule(Callable<V> task, long delay, TimeUnit unit);

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
