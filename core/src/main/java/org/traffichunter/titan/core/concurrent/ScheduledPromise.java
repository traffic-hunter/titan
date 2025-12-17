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

import io.netty.util.internal.PriorityQueueNode;
import java.util.concurrent.Callable;
import java.util.concurrent.Delayed;

import org.traffichunter.titan.core.util.Time;

/**
 * @author yungwang-o
 */
public interface ScheduledPromise<C> extends Promise<C>, Delayed, PriorityQueueNode {

    static <C> ScheduledPromise<C> newPromise(EventLoop eventLoop, Runnable task, long deadlineNanos) {
        return new ScheduledPromiseImpl<>(eventLoop, task, deadlineNanos);
    }

    static <C> ScheduledPromise<C> newPromise(EventLoop eventLoop, Callable<C> task, long deadlineNanos) {
        return new ScheduledPromiseImpl<>(eventLoop, task, deadlineNanos);
    }

    static long calculateDeadlineNanos(final long delay) {
        long deadlineNanos = Time.currentNanos() + delay;
        return deadlineNanos < 0 ? Long.MAX_VALUE : deadlineNanos;
    }

    long getId();

    ScheduledPromise<C> setId(long id);

    long getDeadlineNanos();
}
