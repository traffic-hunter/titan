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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traffichunter.titan.core.util.concurrent.Promise;
import org.traffichunter.titan.core.util.concurrent.ScheduledPromise;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * Event-loop group for server-side accept handling.
 *
 * <p>Transports register {@link NetServerChannel} instances here. Accepted child channels
 * are handed off to the secondary group before read/write readiness is registered.</p>
 *
 * @author yun
 */
public final class ChannelPrimaryIOEventLoopGroup implements ChannelEventLoopGroup<ChannelPrimaryIOEventLoop> {

    private static final Logger log = LoggerFactory.getLogger(ChannelPrimaryIOEventLoopGroup.class);

    private final RoundRobinSelector<ChannelPrimaryIOEventLoop> selector;
    private final List<ChannelPrimaryIOEventLoop> group;

    public ChannelPrimaryIOEventLoopGroup() {
        this(1);
    }

    public ChannelPrimaryIOEventLoopGroup(int size) {
        List<ChannelPrimaryIOEventLoop> eventLoops = new ArrayList<>(size);

        try {
            for (int i = 0; i < size; i++) {
                eventLoops.add(EventLoopFactory.createPrimaryIOEventLoop());
            }
        } catch (Exception e) {
            eventLoops.forEach(IOEventLoop::gracefullyShutdown);
        } finally {
            this.selector = new RoundRobinSelector<>();
            this.group = eventLoops;
        }
    }

    @Override
    public ChannelPrimaryIOEventLoop next() {
        return selector.next(group);
    }

    @Override
    public void start() {
        group.forEach(ChannelPrimaryIOEventLoop::start);
    }

    @Override
    public void register(Channel channel) {
        selector.next(group).register(channel);
    }

    @Override
    public void execute(Runnable task) {
        selector.next(group).execute(task);
    }

    @Override
    public IOSelector ioSelector() {
        return selector.next(group).ioSelector();
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
        return group.stream().anyMatch(eventLoop -> eventLoop.inEventLoop(thread));
    }

    @Override
    public void gracefullyShutdown(long timeout, TimeUnit unit) {
        group.forEach(eventLoop -> eventLoop.gracefullyShutdown(timeout, unit));
    }

    @Override
    public void shutdown() {
        group.forEach(ChannelPrimaryIOEventLoop::shutdown);
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
