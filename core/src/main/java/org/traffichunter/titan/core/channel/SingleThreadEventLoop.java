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
import java.util.Comparator;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.util.BlockingArrayQueue;
import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.bootstrap.Configurations;
import org.traffichunter.titan.core.concurrent.ScheduledPromise;
import org.traffichunter.titan.core.util.Assert;
import org.traffichunter.titan.core.util.Time;

/**
 * @author yungwang-o
 */
@Slf4j
public abstract class SingleThreadEventLoop extends AbstractEventLoop {

    private static final int INITIAL_TASK_QUEUE_CAPACITY = 16;
    private static final Comparator<ScheduledPromise<?>> SCHEDULE_PROMISE_COMPARATOR = ScheduledPromise::compareTo;
    private final AtomicLong taskId = new AtomicLong();

    private final PriorityQueue<ScheduledPromise<?>> scheduleQueue;

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

        execute(() -> {
            thread = Thread.currentThread();

            try {
                doRun();
            } catch (Exception e) {
                log.error("An event loop terminated with unexpected exception. Exception:", e);
            } finally {
                // shutting down
                while (true) {
                    if(getStatus().compareTo(EventLoopStatus.SHUTTING_DOWN) >= 0
                            || trySetStatus(getStatus(), EventLoopStatus.SHUTTING_DOWN)) {
                        break;
                    }
                }
                try {
                    // process remaining task
                    while (true) {
                        if(checkShutdown()) {
                            break;
                        }
                    }

                    // shutdown
                    while (true) {
                        if (getStatus().compareTo(EventLoopStatus.SHUTDOWN) >= 0
                                || trySetStatus(getStatus(), EventLoopStatus.SHUTDOWN)) {
                            break;
                        }
                    }
                } finally {
                    cleanUp();

                    int countTask = runAllTasks();
                    if (countTask > 0) {
                        log.error("An event loop terminated with " + "non-empty task queue ({})", countTask);
                    }
                }
            }
        });
    }

    protected abstract void doRun() throws Exception;

    @Override
    @SuppressWarnings("unchecked")
    public <V> ScheduledPromise<V> schedule(final Runnable task, final long delay, final TimeUnit unit) {
        return (ScheduledPromise<V>) schedule(Executors.callable(task), delay, unit);
    }

    @Override
    public <V> ScheduledPromise<V> schedule(final Callable<V> task, final long delay, final TimeUnit unit) {
        final long calculatedDeadlineNanos = ScheduledPromise.calculateDeadlineNanos(unit.toNanos(delay));

        ScheduledPromise<V> scheduledTask = ScheduledPromise.newPromise(this, task, calculatedDeadlineNanos);

        return schedule(scheduledTask);
    }

    @Override
    public <V> ScheduledPromise<V> scheduleAtFixedRate(Runnable task, long initialDelay, long period, TimeUnit unit) {
        Assert.checkArgument(initialDelay >= 0L, "initial delay must be >= 0");
        Assert.checkArgument(period >= 0L, "period must be >= 0");

        final long calculatedDeadlineNanos = ScheduledPromise.calculateDeadlineNanos(unit.toNanos(initialDelay));

        ScheduledPromise<V> scheduledTask = ScheduledPromise.newPromise(
                this,
                task,
                calculatedDeadlineNanos,
                unit.toNanos(period)
        );

        return schedule(scheduledTask);
    }

    @Override
    public <V> ScheduledPromise<V> scheduleWithFixedDelay(Runnable task, long initialDelay, long period, TimeUnit unit) {
        Assert.checkArgument(initialDelay >= 0L, "initial delay must be >= 0");
        Assert.checkArgument(period >= 0L, "period must be >= 0");

        final long calculatedDeadlineNanos = ScheduledPromise.calculateDeadlineNanos(unit.toNanos(initialDelay));

        ScheduledPromise<V> scheduledTask = ScheduledPromise.newPromise(
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
    public void gracefullyShutdown(final long timeout, final TimeUnit unit) {
        shutdownStartNanos = Time.currentNanos();

        if(isShuttingDown()) {
            wakeUp();
            return;
        }
        final EventLoopStatus oldStatus = getStatus();

        long deadline = System.nanoTime() + unit.toNanos(timeout);

        while (true) {
            if(isShuttingDown()) {
                break;
            }
            if(!taskQueue.isEmpty()) {
                continue;
            }

            long remaining = deadline - System.nanoTime();
            if(remaining <= 0) {
                log.warn("Graceful shutdown timed out, forcing immediate shutdown");
                super.shutdownNow();

                if(!trySetStatus(oldStatus, EventLoopStatus.SHUTDOWN)) {
                    break;
                }
            }

            if(!trySetStatus(oldStatus, EventLoopStatus.SHUTTING_DOWN)) {
                break;
            }

            wakeUp();
            break;
        }
        shutdownTimeoutNanos = unit.toNanos(timeout);
    }

    @Override
    public void register(final Runnable task) {
        addTask(task);
    }

    public void removeScheduledTask(final ScheduledPromise<?> scheduledTask) {
        if(inEventLoop()) {
            scheduleQueue.removeTyped(scheduledTask);
        } else {
            register(() -> scheduleQueue.removeTyped(scheduledTask));
        }
    }

    protected void addTask(final Runnable task) {
        if(isShuttingDown()) {
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
    int runAllTasks() {
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

    protected boolean checkShutdown() {
        if(!isShuttingDown()) {
            return false;
        }
        if(!inEventLoop()) {
            throw new IllegalStateException("Must be invoke as an event loop");
        }

        cancel();
        runAllTasks();
        if(Time.currentNanos() - shutdownStartNanos > shutdownTimeoutNanos) {
            return true;
        }

        return isShutdown();
    }

    protected abstract void cleanUp();

    protected long delayNanosUntilNextScheduledTask() {
        ScheduledPromise<?> scheduleTask = scheduleQueue.peek();
        if (scheduleTask == null) {
            return -1L;
        }

        return scheduleTask.getDeadlineNanos() - Time.currentNanos();
    }

    protected @Nullable Runnable takeTask() {
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
            register(scheduledTask);
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

    private void cancel() {
        scheduleQueue.clear();
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
