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

import io.netty.util.internal.DefaultPriorityQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

import org.traffichunter.titan.core.channel.EventLoop;
import org.traffichunter.titan.core.channel.SingleThreadEventLoop;
import org.traffichunter.titan.core.util.Assert;
import org.traffichunter.titan.core.util.Time;

/**
 * @author yungwang-o
 */
public class ScheduledPromiseImpl<C> extends PromiseImpl<C> implements ScheduledPromise<C> {

    private long id;
    private long deadlineNanos;
    private int queueIndex = INDEX_NOT_IN_QUEUE;
    private final long periodNanos;

    public ScheduledPromiseImpl(final EventLoop eventLoop,
                                final Runnable task,
                                final long deadlineNanos,
                                final long period) {
        super(eventLoop, task);
        Assert.checkArgument(deadlineNanos >= 0, "deadlineNanos cannot be negative");
        this.deadlineNanos = deadlineNanos;
        this.periodNanos = period;
    }

    public ScheduledPromiseImpl(final EventLoop eventLoop, final Runnable task, long deadlineNanos) {
        super(eventLoop, task);
        Assert.checkArgument(deadlineNanos >= 0, "deadlineNanos cannot be negative");
        this.deadlineNanos = deadlineNanos;
        this.periodNanos = 0;
    }

    public ScheduledPromiseImpl(final EventLoop eventLoop, final Callable<C> task, long deadlineNanos) {
        super(eventLoop, task);
        Assert.checkArgument(deadlineNanos >= 0, "deadlineNanos cannot be negative");
        this.deadlineNanos = deadlineNanos;
        this.periodNanos = 0;
    }

    @Override
    public void run() {
        if(!eventLoop.inEventLoop()) {
            return;
        }

        if(!isPeriodic()) {
            super.run();
            return;
        }

        if(isCancelled() || eventLoop.isShuttingDown()) {
            return;
        }

        try {
            if (task != null) {
                task.call();
            }
        } catch (Exception e) {
            fail(e);
            return;
        }

        if(isCancelled() || eventLoop.isShuttingDown() || isDone()) {
            return;
        }

        if(periodNanos > 0) {
            deadlineNanos += periodNanos;
        } else {
            deadlineNanos = ScheduledPromise.calculateDeadlineNanos(-periodNanos);
        }

        eventLoop.register(this);
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
    public boolean cancel(boolean mayInterruptIfRunning) {
        final boolean canceled = super.cancel(mayInterruptIfRunning);
        if(canceled) {
            singleThreadEventLoop().removeScheduledTask(this);
        }

        return canceled;
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

    private static long delayNanos(long currentTimeNanos, long deadlineNanos) {
        return Math.max(0, deadlineNanos - currentTimeNanos);
    }

    private boolean isPeriodic() {
        return periodNanos != 0;
    }

    private SingleThreadEventLoop singleThreadEventLoop() {
        return (SingleThreadEventLoop) eventLoop;
    }
}
