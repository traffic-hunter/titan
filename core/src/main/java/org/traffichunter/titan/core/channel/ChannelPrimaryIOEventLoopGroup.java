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
package org.traffichunter.titan.core.channel;

import lombok.extern.slf4j.Slf4j;
import org.traffichunter.titan.core.concurrent.*;
import org.traffichunter.titan.core.concurrent.Promise;
import org.traffichunter.titan.core.concurrent.ScheduledPromise;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * @author yun
 */
@Slf4j
public final class ChannelPrimaryIOEventLoopGroup implements ChannelEventLoopGroup<ChannelPrimaryIOEventLoop> {

    private final RoundRobinSelector<ChannelPrimaryIOEventLoop> selector;
    private final List<ChannelPrimaryIOEventLoop> group;

    public ChannelPrimaryIOEventLoopGroup() {
        this(1);
    }

    public ChannelPrimaryIOEventLoopGroup(final int size) {
        List<ChannelPrimaryIOEventLoop> eventLoops = new ArrayList<>(size);

        try {
            for (int i = 0; i < size; i++) {
                eventLoops.add(EventLoopFactory.createPrimaryIOEventLoop());
            }
        } catch (Exception e) {
            eventLoops.forEach(IOEventLoop::gracefullyShutdown);
        } finally {
            this.selector = new RoundRobinSelector<>(eventLoops);
            this.group = eventLoops;
        }
    }

    @Override
    public ChannelPrimaryIOEventLoop next() {
        return selector.next();
    }

    @Override
    public void start() {
        group.forEach(ChannelPrimaryIOEventLoop::start);
    }

    @Override
    public void register(Runnable task) {
        selector.next().register(task);
    }

    @Override
    public IOSelector ioSelector() {
        return selector.next().ioSelector();
    }

    @SuppressWarnings("unchecked")
    @Override
    public Promise<?> submit(Runnable task) {
        return selector.next().submit(task);
    }

    @Override
    public <V> Promise<V> submit(Callable<V> task) {
        return selector.next().submit(task);
    }

    @Override
    public <V> ScheduledPromise<V> schedule(Runnable task, long delay, TimeUnit unit) {
        return selector.next().schedule(task, delay, unit);
    }

    @Override
    public <V> ScheduledPromise<V> schedule(Callable<V> task, long delay, TimeUnit unit) {
        return selector.next().schedule(task, delay, unit);
    }

    @Override
    public boolean inEventLoop(Thread thread) {
        return selector.next().inEventLoop(thread);
    }

    @Override
    public void gracefullyShutdown(long timeout, TimeUnit unit) {
        group.forEach(IOEventLoop::gracefullyShutdown);
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
