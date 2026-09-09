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
package org.traffichunter.titan.core.util.concurrent;

import io.netty.util.internal.DefaultPriorityQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

import org.traffichunter.titan.core.channel.EventLoop;
import org.traffichunter.titan.core.channel.SingleThreadEventLoop;
import org.traffichunter.titan.core.util.Assert;
import org.traffichunter.titan.core.util.Time;

/**
 * Default scheduled promise implementation.
 *
 * <p>A one-shot scheduled promise completes through {@link PromiseImpl#run()}. A periodic
 * scheduled promise executes its task, computes the next deadline, and re-enqueues itself
 * with the owning event loop until it is cancelled or the event loop shuts down.</p>
 *
 * @author yungwang-o
 */
public class ScheduledPromiseImpl<C> extends PromiseImpl<C> implements ScheduledPromise<C> {

    private final EventLoop eventLoop;
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
        this.eventLoop = eventLoop;
        this.deadlineNanos = deadlineNanos;
        this.periodNanos = period;
    }

    public ScheduledPromiseImpl(final EventLoop eventLoop, final Runnable task, long deadlineNanos) {
        super(eventLoop, task);
        Assert.checkArgument(deadlineNanos >= 0, "deadlineNanos cannot be negative");
        this.eventLoop = eventLoop;
        this.deadlineNanos = deadlineNanos;
        this.periodNanos = 0;
    }

    public ScheduledPromiseImpl(final EventLoop eventLoop, final Callable<C> task, long deadlineNanos) {
        super(eventLoop, task);
        Assert.checkArgument(deadlineNanos >= 0, "deadlineNanos cannot be negative");
        this.eventLoop = eventLoop;
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

        eventLoop.execute(this);
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
