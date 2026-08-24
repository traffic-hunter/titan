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
package org.traffichunter.titan.core.channel;

import org.traffichunter.titan.core.util.concurrent.Promise;
import org.traffichunter.titan.core.util.concurrent.ScheduledPromise;
import org.traffichunter.titan.core.util.Assert;
import org.traffichunter.titan.core.util.event.EventLoopConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * Event-loop group for work that should not execute on channel I/O event loops.
 *
 * <p>Tasks are distributed across task-only event loops in round-robin order. The group owns
 * the lifecycle of every member loop and exposes their combined lifecycle state.</p>
 *
 * @author yun
 */
public final class WorkerEventLoopGroup implements EventLoopGroup<TaskEventLoop> {

    private final RoundRobinSelector<TaskEventLoop> selector;
    private final List<TaskEventLoop> group;

    public WorkerEventLoopGroup() {
        this(Runtime.getRuntime().availableProcessors());
    }

    public WorkerEventLoopGroup(int size) {
        Assert.checkArgument(size > 0, "Worker event loop group size must be greater than zero");

        List<TaskEventLoop> eventLoops = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            eventLoops.add(new TaskEventLoop(
                    EventLoopConstants.WORKER_EVENT_LOOP_THREAD_NAME + "-" + (i + 1)
            ));
        }

        this.selector = new RoundRobinSelector<>();
        this.group = List.copyOf(eventLoops);
    }

    @Override
    public TaskEventLoop next() {
        return selector.next(group);
    }

    @Override
    public void start() {
        group.forEach(TaskEventLoop::start);
    }

    @Override
    public void execute(Runnable task) {
        next().execute(task);
    }

    @Override
    public Promise<Void> submit(Runnable task) {
        return next().submit(task);
    }

    @Override
    public <V> Promise<V> submit(Callable<V> task) {
        return next().submit(task);
    }

    @Override
    public ScheduledPromise<?> schedule(Runnable task, long delay, TimeUnit unit) {
        return next().schedule(task, delay, unit);
    }

    @Override
    public <V> ScheduledPromise<V> schedule(Callable<V> task, long delay, TimeUnit unit) {
        return next().schedule(task, delay, unit);
    }

    @Override
    public ScheduledPromise<?> scheduleAtFixedRate(Runnable task, long initialDelay, long period, TimeUnit unit) {
        return next().scheduleAtFixedRate(task, initialDelay, period, unit);
    }

    @Override
    public ScheduledPromise<?> scheduleWithFixedDelay(Runnable task, long initialDelay, long period, TimeUnit unit) {
        return next().scheduleWithFixedDelay(task, initialDelay, period, unit);
    }

    @Override
    public boolean inEventLoop(Thread thread) {
        for (EventLoop eventLoop : group) {
            if (eventLoop.inEventLoop(thread)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void gracefullyShutdown(long timeout, TimeUnit unit) {
        group.forEach(eventLoop -> eventLoop.gracefullyShutdown(timeout, unit));
    }

    @Override
    public void close() {
        group.forEach(TaskEventLoop::close);
    }

    @Override
    public boolean isNotStarted() {
        return group.stream().allMatch(EventLoop::isNotStarted);
    }

    @Override
    public boolean isStarted() {
        return group.stream().allMatch(EventLoop::isStarted);
    }

    @Override
    public boolean isShuttingDown() {
        return group.stream().allMatch(EventLoop::isShuttingDown);
    }

    @Override
    public boolean isShutdown() {
        return group.stream().allMatch(EventLoop::isShutdown);
    }

    @Override
    public boolean isTerminated() {
        return group.stream().allMatch(EventLoop::isTerminated);
    }
}
