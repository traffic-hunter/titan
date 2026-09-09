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

import org.traffichunter.titan.core.util.concurrent.Promise;
import org.traffichunter.titan.core.util.concurrent.ScheduledPromise;
import org.traffichunter.titan.core.util.Assert;

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
    public void shutdown() {
        group.forEach(EventLoop::shutdown);
    }

    @Override
    public List<Runnable> shutdownNow() {
        return group.stream()
                .flatMap(el -> el.shutdownNow().stream())
                .toList();
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
