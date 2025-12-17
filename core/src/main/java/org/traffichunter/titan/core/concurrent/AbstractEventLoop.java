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

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import lombok.extern.slf4j.Slf4j;
import org.traffichunter.titan.bootstrap.Configurations;

/**
 * @author yungwang-o
 */
@Slf4j
public abstract class AbstractEventLoop extends AdvancedThreadPoolExecutor implements EventLoop {

    private static final Runnable WAKEUP_TASK = () -> {};

    protected final Queue<Runnable> taskQueue;
    protected volatile Thread thread;

    // Optimize atomicReference
    private static final AtomicReferenceFieldUpdater<AbstractEventLoop, EventLoopStatus> STATUS_UPDATER =
            AtomicReferenceFieldUpdater.newUpdater(AbstractEventLoop.class, EventLoopStatus.class, "status");
    private volatile EventLoopStatus status = EventLoopStatus.NOT_STARTED;
    protected long shutdownStartNanos;
    protected long shutdownTimeoutNanos;

    public AbstractEventLoop(final String eventLoopName, final Queue<Runnable> taskQueue) {
        super(AdvancedThreadPoolExecutor.singleThreadExecutor(eventLoopName, Configurations.taskPendingCapacity()));
        this.taskQueue = taskQueue;
    }

    @Override
    public void start() {
        if(!(status == EventLoopStatus.NOT_STARTED)) {
            return;
        }

        if(!STATUS_UPDATER.compareAndSet(this, EventLoopStatus.NOT_STARTED, EventLoopStatus.STARTED)) {
            return;
        }

        boolean successStart = false;
        try {
            run();
            successStart = true;
        } finally {
            if(!successStart) {
                STATUS_UPDATER.compareAndSet(this, EventLoopStatus.STARTED, EventLoopStatus.NOT_STARTED);
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Promise<?> submit(final Runnable task) {
        Promise<Void> promise = Promise.newPromise(this, task);
        register(promise);
        return promise;
    }

    @Override
    public <T> Promise<T> submit(final Callable<T> task) {
        Promise<T> promise = Promise.newPromise(this, task);
        register(promise);
        return promise;
    }

    @Override
    public void close() {
        super.close();
    }

    @Override
    public void register(final Runnable task) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isNotStarted() {
        return status == EventLoopStatus.NOT_STARTED;
    }

    @Override
    public boolean isStarted() {
        return status == EventLoopStatus.STARTED;
    }

    @Override
    public boolean isShuttingDown() {
        return status.compareTo(EventLoopStatus.SHUTTING_DOWN) >= 0;
    }

    @Override
    public boolean isShutdown() {
        return status.compareTo(EventLoopStatus.SHUTDOWN) >= 0;
    }

    @Override
    public boolean isTerminated() {
        return status.compareTo(EventLoopStatus.TERMINATED) >= 0;
    }

    protected abstract void run();

    void wakeUp() {
        taskQueue.add(WAKEUP_TASK);
    }

    @CanIgnoreReturnValue
    final boolean trySetStatus(final EventLoopStatus oldStatus, final EventLoopStatus newStatus) {
        return STATUS_UPDATER.compareAndSet(this, oldStatus, newStatus);
    }

    final EventLoopStatus getStatus() {
        return this.status;
    }

    final void setStatus(final EventLoopStatus status) {
        STATUS_UPDATER.set(this, status);
    }
}
