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
import io.netty.util.internal.DefaultPriorityQueue;
import io.netty.util.internal.PriorityQueue;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.bootstrap.Configurations;
import org.traffichunter.titan.core.util.concurrent.ScheduledPromise;
import org.traffichunter.titan.core.util.Assert;
import org.traffichunter.titan.core.util.Time;

/**
 * Event loop with one owner thread, an immediate task queue, and a scheduled-task queue.
 *
 * <p>The loop repeatedly moves due scheduled promises into the task queue and runs available
 * tasks on its owner thread. Shutdown is cooperative: the loop first enters
 * {@link EventLoopStatus#SHUTTING_DOWN}, drains queued work until timeout, then performs
 * subclass cleanup.</p>
 *
 * @author yungwang-o
 */
public abstract class SingleThreadEventLoop extends AbstractEventLoop {

    private static final Logger log = LoggerFactory.getLogger(SingleThreadEventLoop.class);

    private static final int INITIAL_TASK_QUEUE_CAPACITY = 16;
    private static final Comparator<ScheduledPromise<?>> SCHEDULE_PROMISE_COMPARATOR = ScheduledPromise::compareTo;
    private final AtomicLong taskId = new AtomicLong();

    private final PriorityQueue<ScheduledPromise<?>> scheduleQueue;
    private boolean shutdownTimeoutEnabled;

    public SingleThreadEventLoop(final String eventLoopName) {
        this(eventLoopName, new ArrayBlockingQueue<>(Math.max(INITIAL_TASK_QUEUE_CAPACITY, Configurations.taskPendingCapacity())));
    }

    protected SingleThreadEventLoop(final String eventLoopName, final Queue<Runnable> taskQueue) {
        super(1, 1, 0L,
                TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(Configurations.taskPendingCapacity()), eventLoopName,
                taskQueue);
        this.scheduleQueue = new DefaultPriorityQueue<>(SCHEDULE_PROMISE_COMPARATOR, INITIAL_TASK_QUEUE_CAPACITY);
    }

    @Override
    protected void run() {
        if(thread != null) {
            return;
        }

        executeEventLoop(() -> {
            thread = Thread.currentThread();

            try {
                doRun();
            } catch (Exception e) {
                log.error("An event loop terminated with unexpected exception. Exception:", e);
            } finally {
                while (true) {
                    if(getStatus().compareTo(EventLoopStatus.SHUTTING_DOWN) >= 0
                            || trySetStatus(getStatus(), EventLoopStatus.SHUTTING_DOWN)) {
                        break;
                    }
                }

                finishShutdown();
            }
        });
    }

    /**
     * Main loop body executed by the owner thread.
     */
    protected abstract void doRun() throws Exception;

    @Override
    public ScheduledPromise<?> schedule(final Runnable task, final long delay, final TimeUnit unit) {
        return schedule(Executors.callable(task), delay, unit);
    }

    @Override
    public <V> ScheduledPromise<V> schedule(final Callable<V> task, final long delay, final TimeUnit unit) {
        final long calculatedDeadlineNanos = ScheduledPromise.calculateDeadlineNanos(unit.toNanos(delay));

        ScheduledPromise<V> scheduledTask = ScheduledPromise.newPromise(this, task, calculatedDeadlineNanos);

        return schedule(scheduledTask);
    }

    @Override
    public ScheduledPromise<?> scheduleAtFixedRate(Runnable task, long initialDelay, long period, TimeUnit unit) {
        Assert.checkArgument(initialDelay >= 0L, "initial delay must be >= 0");
        Assert.checkArgument(period >= 0L, "period must be >= 0");

        final long calculatedDeadlineNanos = ScheduledPromise.calculateDeadlineNanos(unit.toNanos(initialDelay));

        ScheduledPromise<?> scheduledTask = ScheduledPromise.newPromise(
                this,
                task,
                calculatedDeadlineNanos,
                unit.toNanos(period)
        );

        return schedule(scheduledTask);
    }

    @Override
    public ScheduledPromise<?> scheduleWithFixedDelay(Runnable task, long initialDelay, long period, TimeUnit unit) {
        Assert.checkArgument(initialDelay >= 0L, "initial delay must be >= 0");
        Assert.checkArgument(period >= 0L, "period must be >= 0");

        final long calculatedDeadlineNanos = ScheduledPromise.calculateDeadlineNanos(unit.toNanos(initialDelay));

        ScheduledPromise<?> scheduledTask = ScheduledPromise.newPromise(
                this,
                task,
                calculatedDeadlineNanos,
                -unit.toNanos(period)
        );

        return schedule(scheduledTask);
    }

    @Override
    public boolean inEventLoop(final Thread thread) {
        return this.thread == thread;
    }

    @Override
    public synchronized void gracefullyShutdown(final long timeout, final TimeUnit unit) {
        Assert.checkArgument(timeout >= 0, "shutdown timeout must be >= 0");

        beginShutdown(true, unit.toNanos(timeout));
    }

    @Override
    public synchronized void shutdown() {
        beginShutdown(false, 0);
    }

    @Override
    public synchronized List<Runnable> shutdownNow() {
        boolean neverStarted;
        while (true) {
            EventLoopStatus current = getStatus();
            if (current.compareTo(EventLoopStatus.SHUTDOWN) >= 0) {
                return List.of();
            }
            if (trySetStatus(current, EventLoopStatus.SHUTDOWN)) {
                neverStarted = current == EventLoopStatus.NOT_STARTED;
                break;
            }
        }

        List<Runnable> pendingTasks = new ArrayList<>();
        Runnable task;
        while ((task = taskQueue.poll()) != null) {
            pendingTasks.add(task);
            if (task instanceof Future<?> future) {
                future.cancel(false);
            }
        }
        if (neverStarted) {
            cancelScheduleTasks();
            cleanUp();
            pendingTasks.addAll(shutdownExecutorNow());
            return pendingTasks;
        }

        pendingTasks.addAll(shutdownExecutorNow());
        wakeUp();
        return pendingTasks;
    }

    public final void removeScheduledTask(final ScheduledPromise<?> scheduledTask) {
        if(inEventLoop()) {
            scheduleQueue.removeTyped(scheduledTask);
        } else {
            execute(() -> scheduleQueue.removeTyped(scheduledTask));
        }
    }

    @Override
    protected void addTask(final Runnable task) {
        if(getStatus().compareTo(EventLoopStatus.SHUTTING_DOWN) >= 0) {
            throw new RejectedExecutionException("Event loop is shutdown!!");
        }

        if(task instanceof ScheduledPromise<?> promise) {
            scheduleQueue.add(promise);
            return;
        }
        if(!taskQueue.offer(task)) {
            throw new RejectedExecutionException("Failed to add task!!");
        }
    }

    @CanIgnoreReturnValue
    final int runAllTasks() {
        if(!inEventLoop()) {
            return 0;
        }

        int count = 0;
        while (true) {
            Runnable task = takeTask();
            if(task == null) {
                break;
            }

            try {
                count++;
                task.run();
            } catch (Exception e) {
                log.error("Failed to run task! = {}", e.getMessage());
            }
        }

        return count;
    }

    protected final boolean checkShutdown() {
        if(!isShuttingDown()) {
            return false;
        }
        if(!inEventLoop()) {
            throw new IllegalStateException("Must be invoke as an event loop");
        }
        return true;
    }

    /**
     * Releases resources owned by the concrete loop after the run loop terminates.
     */
    protected abstract void cleanUp();

    protected final long delayNanosUntilNextScheduledTask() {
        ScheduledPromise<?> scheduleTask = scheduleQueue.peek();
        long scheduledDelay = scheduleTask == null
                ? -1L
                : scheduleTask.getDeadlineNanos() - Time.currentNanos();
        if (!isShuttingDown() || isShutdown()) {
            return scheduledDelay;
        }

        long shutdownDelay = Math.max(0L, shutdownTimeoutNanos - (Time.currentNanos() - shutdownStartNanos));
        return scheduledDelay < 0 ? shutdownDelay : Math.min(scheduledDelay, shutdownDelay);
    }

    protected final @Nullable Runnable takeTask() {
        if(!inEventLoop()) {
            return null;
        }

        while (true) {
            if(scheduleQueue.peek() == null) {
                return taskQueue.poll();
            }

            loadTaskAfterCompletedScheduledTask();
            return taskQueue.poll();
        }
    }

    private <V> ScheduledPromise<V> schedule(final ScheduledPromise<V> scheduledTask) {
        final long taskId = this.taskId.incrementAndGet();

        if(!inEventLoop()) {
            execute(scheduledTask);
            return scheduledTask;
        }

        scheduleQueue.add(scheduledTask.setId(taskId));
        return scheduledTask;
    }

    private void loadTaskAfterCompletedScheduledTask() {
        if(scheduleQueue.isEmpty()) {
            return;
        }

        while (true) {
            Runnable scheduledTask = pollScheduledTask(Time.currentNanos());
            if(scheduledTask == null) {
                break;
            }

            boolean isAdd = taskQueue.add(scheduledTask);
            if(isAdd) {
                continue;
            }

            scheduleQueue.add((ScheduledPromise<?>) scheduledTask);
        }
    }

    private @Nullable Runnable pollScheduledTask(final long nanos) {
        if(!inEventLoop()) {
            return null;
        }

        ScheduledPromise<?> scheduleTask = scheduleQueue.peek();
        if(scheduleTask == null || scheduleTask.getDeadlineNanos() - nanos > 0) {
            return null;
        }

        scheduleQueue.remove();
        return scheduleTask;
    }

    private void cancelScheduleTasks() {
        ScheduledPromise<?> scheduledTask;
        while ((scheduledTask = scheduleQueue.poll()) != null) {
            scheduledTask.cancel(false);
        }
    }

    private int cancelRemainingTasks() {
        int cancelledTasks = 0;
        Runnable task;
        while ((task = taskQueue.poll()) != null) {
            if (task instanceof Future<?> future) {
                future.cancel(false);
            }
            cancelledTasks++;
        }
        return cancelledTasks;
    }

    private boolean timeoutExceeded() {
        return shutdownTimeoutEnabled
                && Time.currentNanos() - shutdownStartNanos >= shutdownTimeoutNanos;
    }

    private void finishShutdown() {
        try {
            cancelScheduleTasks();
            if (!isShutdown()) {
                while (!timeoutExceeded()) {
                    Runnable task = takeTask();
                    if (task == null) {
                        break;
                    }
                    try {
                        task.run();
                    } catch (Exception error) {
                        log.error("Failed to run task during shutdown", error);
                    }
                }
            }
        } finally {
            setStatus(EventLoopStatus.SHUTDOWN);
            try {
                cleanUp();
            } finally {
                cancelRemainingTasks();
                shutdownExecutor();
            }
        }
    }

    private void beginShutdown(boolean timeoutEnabled, long timeoutNanos) {
        if (isShuttingDown()) {
            wakeUp();
            return;
        }

        if (isNotStarted()) {
            start();
        }

        shutdownStartNanos = Time.currentNanos();
        shutdownTimeoutEnabled = timeoutEnabled;
        shutdownTimeoutNanos = timeoutNanos;
        while (true) {
            EventLoopStatus current = getStatus();
            if (isShuttingDown() || trySetStatus(current, EventLoopStatus.SHUTTING_DOWN)) {
                break;
            }
        }
        wakeUp();
    }

    @Deprecated(forRemoval = true)
    private void doShutdown(final long timeout, final TimeUnit unit) {
        super.shutdown();
        try {
            if(!awaitTermination(timeout, unit)) {
                shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
