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
package org.traffichunter.titan.core.event;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.traffichunter.titan.bootstrap.GlobalShutdownHook;
import org.traffichunter.titan.core.util.Assert;
import org.traffichunter.titan.core.util.concurrent.AdvancedThreadPoolExecutor;

/**
 * @author yungwang-o
 */
@Slf4j
public abstract class AbstractNioEventLoop extends AdvancedThreadPoolExecutor implements EventLoop {

    @Getter
    private final String eventLoopName;

    protected final Selector selector;
    private final EventLoopLifeCycle lifeCycle = new EventLoopLifeCycleImpl();
    private final Queue<Runnable> pendingTasks = new ConcurrentLinkedQueue<>();

    // Optimize atomicReference
    private static final AtomicReferenceFieldUpdater<AbstractNioEventLoop, EventLoopStatus> STATUS_UPDATER =
            AtomicReferenceFieldUpdater.newUpdater(AbstractNioEventLoop.class, EventLoopStatus.class, "status");
    private volatile EventLoopStatus status;

    private static final GlobalShutdownHook shutdownHook = GlobalShutdownHook.INSTANCE;

    public AbstractNioEventLoop(final String eventLoopName, final int isPendingMaxTasksCapacity) {
        super(AdvancedThreadPoolExecutor.singleThreadExecutor(eventLoopName, isPendingMaxTasksCapacity));
        this.eventLoopName = eventLoopName;
        try {
            this.selector = Selector.open();
        } catch (IOException e) {
            throw new EventLoopException("Select open error!!", e);
        }
        this.status = EventLoopStatus.INITIALIZED;
    }

    public int size() {
        return super.getQueue().size();
    }

    public boolean isEmpty() {
        return super.getQueue().isEmpty();
    }

    public boolean inEventLoop(final String eventLoopName) {
        return Thread.currentThread().getName().equals(eventLoopName);
    }

    protected void registerPendingTask(final Runnable task) {
        pendingTasks.offer(task);
    }

    @Override
    public void start() {
        Assert.check(lifeCycle.isInitialized(), () -> new EventLoopException("EventLoop is not initialized"));

        STATUS_UPDATER.compareAndSet(this, status, EventLoopStatus.STARTING);
        execute(this::runTask);
    }

    @Override
    public void restart() {
        Assert.check(lifeCycle.isSuspended(), () -> new EventLoopException("EventLoop is not suspended"));

        log.info("Restarting event loop...");
        STATUS_UPDATER.compareAndSet(this, status, EventLoopStatus.STARTING);
        resume();
    }

    @Override
    public EventLoopLifeCycle getLifeCycle() {
        return lifeCycle;
    }

    @Override
    public void suspend() {
        Assert.check(lifeCycle.isStarting(), () -> new EventLoopException("EventLoop is not started"));

        STATUS_UPDATER.compareAndSet(this, status, EventLoopStatus.SUSPENDING);
        pause();
        STATUS_UPDATER.compareAndSet(this, status, EventLoopStatus.SUSPENDED);
    }

    @Override
    public void shutdown(final boolean isGraceful, final long timeout, final TimeUnit unit) {
        Assert.check(lifeCycle.isStarting(), () -> new EventLoopException("EventLoop is not started"));

        STATUS_UPDATER.compareAndSet(this, status, EventLoopStatus.STOPPING);

        if (isGraceful) {
            shutdownHook.addShutdownCallback(() -> doShutdown(timeout, unit));
        } else {
            doShutdown(timeout, unit);
        }

        STATUS_UPDATER.compareAndSet(this, status, EventLoopStatus.STOPPED);
    }

    @Override
    public void close() {
        Assert.check(lifeCycle.isStarting(), () -> new EventLoopException("EventLoop is not started"));

        STATUS_UPDATER.compareAndSet(this, status, EventLoopStatus.STOPPING);
        super.close();
        STATUS_UPDATER.compareAndSet(this, status, EventLoopStatus.STOPPED);
    }

    protected abstract void handleIO(Set<SelectionKey> keySet);

    private void doShutdown(long timeout, TimeUnit unit) {
        super.shutdown();
        try {
            if (!awaitTermination(timeout, unit)) {
                super.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            try {
                selector.close();
            } catch (IOException e) {
                log.error("Failed to close selector", e);
            }
        }
    }

    private void runTask() {
        Assert.checkState(selector.isOpen(), "Selector is not open");

        log.info("Starting event loop");

        while (lifeCycle.isStarting() && !Thread.currentThread().isInterrupted()) {
            try {
                processPendingTasks();
                int selected = selector.select();
                if (selected == 0) {
                    continue;
                }
            } catch (IOException e) {
                Thread.currentThread().interrupt();
                log.error("Selector failed: {}", e.getMessage());
                break;
            }

            handleIO(selector.selectedKeys());
        }
    }

    private void processPendingTasks() {
        Runnable task;

        while ((task = pendingTasks.poll()) != null) {
            try {
                task.run();
            } catch (Exception e) {
                log.error("Error processing task: {}", e.getMessage());
            }
        }
    }

    protected class EventLoopLifeCycleImpl implements EventLoopLifeCycle {

        @Override
        public boolean isSuspending() { return status == EventLoopStatus.SUSPENDING; }

        @Override
        public boolean isSuspended() { return status == EventLoopStatus.SUSPENDED; }

        @Override
        public boolean isInitialized() { return status == EventLoopStatus.INITIALIZED; }

        @Override
        public boolean isStarting() { return status == EventLoopStatus.STARTING; }

        @Override
        public boolean isStopping() { return status == EventLoopStatus.STOPPING; }

        @Override
        public boolean isStopped() { return status == EventLoopStatus.STOPPED; }
    }
}
