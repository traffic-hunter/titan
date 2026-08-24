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

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.Collection;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.util.concurrent.Promise;

/**
 * Base event-loop implementation backed by a single-thread executor.
 *
 * <p>This class owns lifecycle state and promise submission. Concrete subclasses decide how
 * the loop body runs: task-only loops can just drain tasks, while I/O loops combine task
 * execution with selector polling.</p>
 *
 * @author yungwang-o
 */
public abstract class AbstractEventLoop extends ThreadPoolExecutor implements EventLoop {

    private static final Logger log = LoggerFactory.getLogger(AbstractEventLoop.class);

    private static final Runnable WAKEUP_TASK = () -> {};

    protected final Queue<Runnable> taskQueue;
    protected @Nullable volatile Thread thread;

    // Atomic updater keeps lifecycle transitions cheap without synchronizing every status read.
    private static final AtomicReferenceFieldUpdater<AbstractEventLoop, EventLoopStatus> STATUS_UPDATER =
            AtomicReferenceFieldUpdater.newUpdater(AbstractEventLoop.class, EventLoopStatus.class, "status");
    private volatile EventLoopStatus status = EventLoopStatus.NOT_STARTED;
    protected long shutdownStartNanos;
    protected long shutdownTimeoutNanos;

    public AbstractEventLoop(
            int corePoolSize,
            int maxPoolSize,
            long keepAliveTime,
            TimeUnit timeUnit,
            BlockingQueue<Runnable> blockingQueue,
            String eventLoopName,
            Queue<Runnable> taskQueue
    ) {
        super(corePoolSize, maxPoolSize, keepAliveTime, timeUnit, blockingQueue, r -> new Thread(r, eventLoopName));
        this.taskQueue = taskQueue;
    }

    public AbstractEventLoop(
            int corePoolSize,
            int maxPoolSize,
            long keepAliveTime,
            TimeUnit timeUnit,
            BlockingQueue<Runnable> blockingQueue,
            String eventLoopName,
            Queue<Runnable> taskQueue,
            RejectedExecutionHandler rejectedExecutionHandler
    ) {
        super(corePoolSize, maxPoolSize, keepAliveTime, timeUnit, blockingQueue, r -> new Thread(r, eventLoopName), rejectedExecutionHandler);
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

    /**
     * Routes external executor submissions through the event-loop task queue.
     */
    @Override
    public final void execute(Runnable task) {
        addTask(task);
    }

    /**
     * Starts the long-running event-loop body on the backing executor thread.
     *
     * <p>This is intentionally separate from {@link #execute(Runnable)} because regular
     * tasks must be consumed from the event-loop task queue.</p>
     */
    protected final void executeEventLoop(Runnable task) {
        super.execute(task);
    }

    @Override
    public Promise<Void> submit(final Runnable task) {
        Promise<Void> promise = Promise.newPromise(this, task);
        execute(promise);
        return promise;
    }

    @Override
    public <T> Promise<T> submit(final Callable<T> task) {
        Promise<T> promise = Promise.newPromise(this, task);
        execute(promise);
        return promise;
    }

    @Override
    public <T> Promise<T> submit(final Runnable task, final T result) {
        return submit(Executors.callable(task, result));
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
            throws InterruptedException {
        rejectBulkInvocationFromEventLoop("invokeAll");
        return super.invokeAll(tasks);
    }

    @Override
    public <T> List<Future<T>> invokeAll(
            Collection<? extends Callable<T>> tasks,
            long timeout,
            TimeUnit unit
    ) throws InterruptedException {
        rejectBulkInvocationFromEventLoop("invokeAll");
        return super.invokeAll(tasks, timeout, unit);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
            throws InterruptedException, ExecutionException {
        rejectBulkInvocationFromEventLoop("invokeAny");
        return super.invokeAny(tasks);
    }

    @Override
    public <T> T invokeAny(
            Collection<? extends Callable<T>> tasks,
            long timeout,
            TimeUnit unit
    ) throws InterruptedException, ExecutionException, TimeoutException {
        rejectBulkInvocationFromEventLoop("invokeAny");
        return super.invokeAny(tasks, timeout, unit);
    }

    @Override
    public void shutdown() {
        gracefullyShutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
        gracefullyShutdown(0, TimeUnit.NANOSECONDS);
        return List.of();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return super.awaitTermination(timeout, unit);
    }

    @Override
    public void close() {
        shutdown();
    }

    protected final void shutdownExecutor() {
        super.shutdown();
    }

    protected final List<Runnable> shutdownExecutorNow() {
        return super.shutdownNow();
    }

    private void rejectBulkInvocationFromEventLoop(String operation) {
        if (inEventLoop()) {
            throw new RejectedExecutionException(
                    "Calling " + operation + " from within the event loop is not allowed"
            );
        }
    }

    @Override
    protected void terminated() {
        setStatus(EventLoopStatus.TERMINATED);
        super.terminated();
    }

    protected abstract void addTask(Runnable task);

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
