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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * Event-loop group for connection read/write processing.
 *
 * <p>Outbound client channels and accepted server child channels are registered here. Work
 * is distributed across member loops with a round-robin selector.</p>
 *
 * @author yun
 */
public final class ChannelSecondaryIOEventLoopGroup implements ChannelEventLoopGroup<ChannelSecondaryIOEventLoop> {

    private final RoundRobinSelector<ChannelSecondaryIOEventLoop> selector;
    private final List<ChannelSecondaryIOEventLoop> group;

    public ChannelSecondaryIOEventLoopGroup() {
        this(Runtime.getRuntime().availableProcessors() * 2);
    }

    public ChannelSecondaryIOEventLoopGroup(final int size) {
        List<ChannelSecondaryIOEventLoop> eventLoops = new ArrayList<>(size);

        try {
            for (int i = 0; i < size; i++) {
                eventLoops.add(EventLoopFactory.createSecondaryIOEventLoop(i + 1));
            }
        } catch (Exception e) {
            eventLoops.forEach(IOEventLoop::gracefullyShutdown);
        } finally {
            this.selector = new RoundRobinSelector<>();
            this.group = eventLoops;
        }
    }

    @Override
    public ChannelSecondaryIOEventLoop next() {
        return selector.next(group);
    }

    @Override
    public void start() {
        group.forEach(ChannelSecondaryIOEventLoop::start);
    }

    @Override
    public void register(Channel channel) {
        ChannelSecondaryIOEventLoop eventLoop = selector.next(group);
        eventLoop.register(channel);
    }

    @Override
    public void execute(Runnable task) {
        selector.next(group).execute(task);
    }

    @Override
    public Promise<Void> submit(Runnable task) {
        return selector.next(group).submit(task);
    }

    @Override
    public <V> Promise<V> submit(Callable<V> task) {
        return selector.next(group).submit(task);
    }

    @Override
    public ScheduledPromise<?> schedule(Runnable task, long delay, TimeUnit unit) {
        return selector.next(group).schedule(task, delay, unit);
    }

    @Override
    public <V> ScheduledPromise<V> schedule(Callable<V> task, long delay, TimeUnit unit) {
        return selector.next(group).schedule(task, delay, unit);
    }

    @Override
    public ScheduledPromise<?> scheduleAtFixedRate(Runnable task, long initialDelay, long period, TimeUnit unit) {
        return selector.next(group).scheduleAtFixedRate(task, initialDelay, period, unit);
    }

    @Override
    public ScheduledPromise<?> scheduleWithFixedDelay(Runnable task, long initialDelay, long period, TimeUnit unit) {
        return selector.next(group).scheduleWithFixedDelay(task, initialDelay, period, unit);
    }

    @Override
    public boolean inEventLoop(Thread thread) {
        return group.stream().anyMatch(el -> el.inEventLoop(thread));
    }

    @Override
    public void gracefullyShutdown(long timeout, TimeUnit unit) {
        group.forEach(eventLoop -> eventLoop.gracefullyShutdown(timeout, unit));
    }

    @Override
    public IOSelector ioSelector() {
        return selector.next(group).ioSelector();
    }

    @Override
    public void shutdown() {
        group.forEach(ChannelSecondaryIOEventLoop::shutdown);
    }

    @Override
    public List<Runnable> shutdownNow() {
        return group.stream()
                .flatMap(el -> el.shutdownNow().stream())
                .toList();
    }

    @Override
    public void close() {
        group.forEach(IOEventLoop::close);
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
