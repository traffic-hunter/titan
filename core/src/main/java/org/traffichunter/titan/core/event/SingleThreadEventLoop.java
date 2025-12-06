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

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.netty.util.internal.DefaultPriorityQueue;
import io.netty.util.internal.PriorityQueue;
import java.util.Comparator;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.util.BlockingArrayQueue;
import org.traffichunter.titan.bootstrap.Configurations;
import org.traffichunter.titan.core.util.Assert;
import org.traffichunter.titan.core.util.Time;
import org.traffichunter.titan.core.util.concurrent.ScheduledPromise;
import org.traffichunter.titan.core.util.event.EventLoopConstants;

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
        this(eventLoopName, new BlockingArrayQueue<>(Math.max(INITIAL_TASK_QUEUE_CAPACITY, Configurations.taskPendingCapacity())));
    }

    protected SingleThreadEventLoop(final String eventLoopName, final Queue<Runnable> taskQueue) {
        super(eventLoopName, taskQueue);
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
        Assert.checkNull(task, "task is null");
        Assert.checkNull(unit, "unit is null");

        long tempDelay = delay;
        if(tempDelay < 0L) {
            tempDelay = 0L;
        }

        long taskId = this.taskId.incrementAndGet();
        final long calculatedDeadlineNanos = ScheduledPromise.calculateDeadlineNanos(unit.toNanos(tempDelay));

        ScheduledPromise<V> scheduledTask = ScheduledPromise.newPromise(this, task, calculatedDeadlineNanos);

        if(!inEventLoop()) {
            register(scheduledTask);
            return scheduledTask;
        }

        scheduleQueue.add(scheduledTask.setId(taskId));
        return scheduledTask;
    }

    @Override
    public boolean inEventLoop(final Thread thread) {
        return this.thread == thread;
    }

    @Override
    public void gracefullyShutdown(final long timeout, final TimeUnit unit) {
        shutdownStartNanos = Time.currentNanos();

        if(isShuttingDown()) {
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
        }
        shutdownTimeoutNanos = unit.toNanos(timeout);
    }

    @Override
    public void register(final Runnable task) {
        Assert.checkNull(task, "task is null");
        addTask(task);
    }

    private void addTask(final Runnable task) {
        Assert.checkNull(task, "task is null");
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
    private int runAllTasks() {
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

    protected Runnable takeTask() {
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

    private Runnable pollScheduledTask(final long nanos) {
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

    @Deprecated
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
