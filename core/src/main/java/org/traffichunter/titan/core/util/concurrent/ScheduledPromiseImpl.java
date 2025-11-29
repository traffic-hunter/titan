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
package org.traffichunter.titan.core.util.concurrent;

import io.netty.util.internal.DefaultPriorityQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import org.traffichunter.titan.core.event.EventLoop;
import org.traffichunter.titan.core.util.Time;

/**
 * @author yungwang-o
 */
public class ScheduledPromiseImpl<C> extends PromiseImpl<C> implements ScheduledPromise<C> {

    private long id;
    private final long deadlineNanos;
    private int queueIndex = INDEX_NOT_IN_QUEUE;

    public ScheduledPromiseImpl(final EventLoop eventLoop, final Runnable task, long deadlineNanos) {
        super(eventLoop, task);
        this.deadlineNanos = deadlineNanos;
    }

    public ScheduledPromiseImpl(final EventLoop eventLoop, final Callable<C> task, long deadlineNanos) {
        super(eventLoop, task);
        this.deadlineNanos = deadlineNanos;
    }

    @Override
    public ScheduledPromise<C> setId(final long id) {
        this.id = id;
        return this;
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public long getDelay(final TimeUnit unit) {
        return unit.toNanos(delayNanos(Time.currentNanos(), deadlineNanos));
    }

    @Override
    public int compareTo(final Delayed o) {
        if (this == o) {
            return 0;
        }
        ScheduledPromise<?> that = (ScheduledPromise<?>) o;
        long d = deadlineNanos - that.getDeadlineNanos();
        if (d < 0) {
            return -1;
        } else if (d > 0) {
            return 1;
        } else if (this.id < that.getId()) {
            return -1;
        } else {
            return 1;
        }
    }

    @Override
    public long getDeadlineNanos() {
        return this.deadlineNanos;
    }

    @Override
    public int priorityQueueIndex(final DefaultPriorityQueue<?> defaultPriorityQueue) {
        return queueIndex;
    }

    @Override
    public void priorityQueueIndex(final DefaultPriorityQueue<?> defaultPriorityQueue, final int i) {
        queueIndex = i;
    }

    private static long delayNanos(long currenTimeNanos, long deadlineNanos) {
        return Math.max(0, deadlineNanos - currenTimeNanos);
    }
}
