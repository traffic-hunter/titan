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

import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import lombok.extern.slf4j.Slf4j;
import org.traffichunter.titan.bootstrap.Configurations;
import org.traffichunter.titan.bootstrap.GlobalShutdownHook;
import org.traffichunter.titan.core.event.SingleEventLoop.EventLoopLifeCycleImpl.EventLoopStatus;
import org.traffichunter.titan.core.util.concurrent.ThreadSafe;
import org.traffichunter.titan.core.util.concurrent.AdvancedThreadPoolExecutor;

/**
 * This implementation is single thread Event Loop
 *
 * @author yungwang-o
 */
@Slf4j
final class SingleEventLoop implements EventLoop {

    private static final String EVENT_LOOP_THREAD_NAME = "EventLoopWorkerThread";

    private final AdvancedThreadPoolExecutor taskExecutors;

    private final EventLoopLifeCycle lifeCycle = new EventLoopLifeCycleImpl();

    // Optimize atomicReference
    private static final AtomicReferenceFieldUpdater<SingleEventLoop, EventLoopStatus> STATUS_UPDATER =
            AtomicReferenceFieldUpdater.newUpdater(SingleEventLoop.class, EventLoopStatus.class, "status");
    private volatile EventLoopStatus status;

    private static final GlobalShutdownHook shutdownHook = GlobalShutdownHook.INSTANCE;

    public SingleEventLoop() {
        this(Configurations.taskPendingCapacity());
    }

    private SingleEventLoop(final int isPendingMaxTasksCapacity) {
        if(isPendingMaxTasksCapacity <= 0) {
            throw new IllegalArgumentException("Task pending max tasks capacity must be greater than zero");
        }

        this.status = EventLoopStatus.NOT_INITIALIZED;
        this.taskExecutors = Objects.requireNonNull(
                initializeThreadPoolExecutors(isPendingMaxTasksCapacity),
                "initializeThreadPoolExecutors"
        );
        this.taskExecutors.allowCoreThreadTimeOut(false);
        this.status = EventLoopStatus.INITIALIZED;
    }

    @Override
    public void start() {
        if(lifeCycle.isInitialized()) {
            log.info("Starting event loop");
            STATUS_UPDATER.compareAndSet(this, status, EventLoopStatus.STARTING);
        } else {
            throw new EventLoopException("EventLoop is not initialized");
        }
    }

    @Override
    @ThreadSafe
    public void restart() {
        if(lifeCycle.isSuspended()) {
            log.info("Restarting event loop");
            STATUS_UPDATER.compareAndSet(this, status, EventLoopStatus.STARTING);
            taskExecutors.resume();
        } else {
            throw new EventLoopException("EventLoop is not suspended");
        }
    }

    @Override
    public <T> CompletableFuture<T> submit(final Callable<T> task) {
        if(task == null) {
            throw new EventLoopException("Task is null");
        }

        if(lifeCycle.isStarting()) {
            throw new EventLoopException("EventLoop is not started");
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                return task.call();
            } catch (Exception e) {
                throw new EventLoopException(e);
            }
        }, taskExecutors);
    }

    @Override
    public CompletableFuture<Void> submit(final Runnable task) {
        if(task == null) {
            throw new EventLoopException("Task is null");
        }

        if(lifeCycle.isStarting()) {
            throw new EventLoopException("EventLoop is not started");
        }

        return CompletableFuture.runAsync(task, taskExecutors);
    }

    private Runnable pollTask() {
        return taskQueue().poll();
    }

    @Override
    public EventLoopLifeCycle getLifeCycle() {
        return lifeCycle;
    }

    public int size() {
        return taskQueue().size();
    }

    public boolean isEmpty() {
        return taskQueue().isEmpty();
    }

    public boolean isInEvnetLoop() {
        return Thread.currentThread().getName().equals(EVENT_LOOP_THREAD_NAME);
    }

    @Override
    public void execute(final Runnable task) {
        if(task == null) {
            throw new EventLoopException("Task is null");
        }

        if(lifeCycle.isStarting()) {
            throw new EventLoopException("EventLoop is not started");
        }

        taskExecutors.execute(task);
    }

    @Override
    public ThreadPoolExecutor eventLoopExecutor() {
        return taskExecutors;
    }

    @Override
    @ThreadSafe
    public void suspend() {

        if(lifeCycle.isStarting()) {
            throw new EventLoopException("EventLoop is not started");
        }

        STATUS_UPDATER.compareAndSet(this, status, EventLoopStatus.SUSPENDING);

        taskExecutors.pause();

        STATUS_UPDATER.compareAndSet(this, status, EventLoopStatus.SUSPENDED);
    }

    @Override
    @ThreadSafe
    public void shutdown(final boolean isGraceful, final long timeout, final TimeUnit unit) {

        if(lifeCycle.isStarting()) {
            throw new EventLoopException("EventLoop is not started");
        }

        STATUS_UPDATER.compareAndSet(this, status, EventLoopStatus.STOPPING);

        if(isGraceful) {
            log.info("is graceful shutdown");
            shutdownHook.addShutdownCallback(() -> shutdown(timeout, unit));
        } else {
            log.info("shutdown");
            shutdown(timeout, unit);
        }

        STATUS_UPDATER.compareAndSet(this, status, EventLoopStatus.STOPPED);
    }

    /**
     * Initiates an orderly shutdown in which previously submitted tasks are executed,
     * but no new tasks will be accepted. waiting 1day
     */
    @Override
    public void close() {
        if(lifeCycle.isStarting()) {
            throw new EventLoopException("EventLoop is not started");
        }

        log.info("Closing event loop");

        STATUS_UPDATER.compareAndSet(this, status, EventLoopStatus.STOPPING);

        taskExecutors.close();

        STATUS_UPDATER.compareAndSet(this, status, EventLoopStatus.STOPPED);
    }

    private Runnable peekTask() {
        if(taskQueue().isEmpty()) {
            throw new EventLoopException("Task is empty");
        }

        return taskQueue().peek();
    }

    @ThreadSafe
    private void shutdown(final long timeout, final TimeUnit unit) {

        taskExecutors.shutdown();
        try {

            if (!taskExecutors.awaitTermination(timeout, unit)) {
                taskExecutors.shutdownNow();
            }
        } catch (InterruptedException e) {
            log.info("interrupted while shutting down");
            taskExecutors.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private BlockingQueue<Runnable> taskQueue() {
        return taskExecutors.getQueue();
    }

    private static AdvancedThreadPoolExecutor initializeThreadPoolExecutors(final int isPendingMaxTasksCapacity) {
        return new AdvancedThreadPoolExecutor(
                1, 1,
                0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(isPendingMaxTasksCapacity),
                (r) -> new Thread(r, EVENT_LOOP_THREAD_NAME)
        );
    }

    class EventLoopLifeCycleImpl implements EventLoopLifeCycle {

        enum EventLoopStatus {
            NOT_INITIALIZED, INITIALIZED, SUSPENDING, SUSPENDED, STARTING, STOPPING, STOPPED;
        }

        @Override
        public boolean isNotInitialized() {
            return EventLoopStatus.NOT_INITIALIZED == status;
        }

        @Override
        public boolean isSuspending() {
            return EventLoopStatus.SUSPENDING == status;
        }

        @Override
        public boolean isSuspended() {
            return EventLoopStatus.SUSPENDED == status;
        }

        @Override
        public boolean isInitialized() {
            return EventLoopStatus.INITIALIZED == status;
        }

        @Override
        public boolean isStarting() {
            return EventLoopStatus.STARTING == status;
        }

        @Override
        public boolean isStopping() {
            return EventLoopStatus.STOPPING == status;
        }

        @Override
        public boolean isStopped() {
            return EventLoopStatus.STOPPED == status;
        }
    }

    private static class EventLoopException extends RuntimeException {

        public EventLoopException() {
        }

        public EventLoopException(final String message) {
            super(message);
        }

        public EventLoopException(final String message, final Throwable cause) {
            super(message, cause);
        }

        public EventLoopException(final Throwable cause) {
            super(cause);
        }

        public EventLoopException(final String message,
                                  final Throwable cause,
                                  final boolean enableSuppression,
                                  final boolean writableStackTrace) {

            super(message, cause, enableSuppression, writableStackTrace);
        }
    }
}