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

import io.netty.util.internal.PriorityQueueNode;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledFuture;

import org.traffichunter.titan.core.channel.EventLoop;
import org.traffichunter.titan.core.util.Time;

/**
 * Promise scheduled by an event loop deadline.
 *
 * <p>Scheduled promises are stored in the event loop's priority queue. They also implement
 * Netty's {@link PriorityQueueNode} so queue index bookkeeping can be updated without an
 * additional wrapper object.</p>
 *
 * @author yungwang-o
 */
public interface ScheduledPromise<C> extends Promise<C>, ScheduledFuture<C>, PriorityQueueNode {

    static <C> ScheduledPromise<C> newPromise(EventLoop eventLoop, Runnable task, long deadlineNanos) {
        return new ScheduledPromiseImpl<>(eventLoop, task, deadlineNanos);
    }

    static <C> ScheduledPromise<C> newPromise(EventLoop eventLoop, Callable<C> task, long deadlineNanos) {
        return new ScheduledPromiseImpl<>(eventLoop, task, deadlineNanos);
    }

    static <C> ScheduledPromise<C> newPromise(EventLoop eventLoop, Runnable task, long deadlineNanos, long period) {
        return new ScheduledPromiseImpl<>(eventLoop, task, deadlineNanos, period);
    }

    /**
     * Calculates an absolute deadline from the event-loop time source.
     */
    static long calculateDeadlineNanos(final long delay) {
        long deadlineNanos = Time.currentNanos() + delay;
        return deadlineNanos < 0 ? Long.MAX_VALUE : deadlineNanos;
    }

    long getId();

    ScheduledPromise<C> setId(long id);

    long getDeadlineNanos();
}
