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
package org.traffichunter.titan.core.util.concurrent;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread-pool executor with cooperative pause/resume support.
 *
 * <p>Paused executors keep accepting queued work, but worker threads block in
 * {@link #beforeExecute(Thread, Runnable)} until {@link #resume()} is called.</p>
 *
 * @author yungwang-o
 */
public class AdvancedThreadPoolExecutor extends ThreadPoolExecutor implements Pausable {

    private final Runnable NOOP = () -> { };

    private final Lock pauseLock = new ReentrantLock();

    private final Condition pausedCond = pauseLock.newCondition();

    private boolean isPaused = false;

    public AdvancedThreadPoolExecutor(final AdvancedThreadPoolExecutor executor) {
        this(
                executor.getCorePoolSize(),
                executor.getMaximumPoolSize(),
                executor.getKeepAliveTime(TimeUnit.MILLISECONDS),
                TimeUnit.MILLISECONDS,
                executor.getQueue(),
                executor.getThreadFactory(),
                false
        );
        allowCoreThreadTimeOut(false);
    }

    public AdvancedThreadPoolExecutor(final int corePoolSize,
                                      final int maximumPoolSize,
                                      final long keepAliveTime,
                                      final TimeUnit unit,
                                      final BlockingQueue<Runnable> workQueue,
                                      final boolean isPaused) {

        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
        this.isPaused = isPaused;
    }

    public AdvancedThreadPoolExecutor(final int corePoolSize,
                                      final int maximumPoolSize,
                                      final long keepAliveTime,
                                      final TimeUnit unit,
                                      final BlockingQueue<Runnable> workQueue,
                                      final ThreadFactory threadFactory,
                                      final boolean isPaused) {

        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory);
        this.isPaused = isPaused;
    }

    public AdvancedThreadPoolExecutor(final int corePoolSize,
                                      final int maximumPoolSize,
                                      final long keepAliveTime,
                                      final TimeUnit unit,
                                      final BlockingQueue<Runnable> workQueue,
                                      final RejectedExecutionHandler handler) {

        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, handler);
    }

    public AdvancedThreadPoolExecutor(final int corePoolSize,
                                      final int maximumPoolSize,
                                      final long keepAliveTime,
                                      final TimeUnit unit,
                                      final BlockingQueue<Runnable> workQueue,
                                      final ThreadFactory threadFactory,
                                      final RejectedExecutionHandler handler) {

        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler);
    }

    public static AdvancedThreadPoolExecutor singleThreadExecutor(final String executorName,
                                                                  final int isPendingMaxTasksCapacity) {
        return new AdvancedThreadPoolExecutor(
                1, 1,
                0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(isPendingMaxTasksCapacity),
                r -> new Thread(r, executorName),
                false
        );
    }

    @Override
    protected void beforeExecute(final Thread t, final Runnable r) {
        super.beforeExecute(t, r);
        pauseLock.lock();
        try {
            while (isPaused) {
                pausedCond.await();
            }
        } catch (InterruptedException e) {
            t.interrupt();
        } finally {
            pauseLock.unlock();
        }
    }

    @Override
    public void pause() {
        pauseLock.lock();
        try {
            isPaused = true;
        } finally {
            pauseLock.unlock();
        }
    }

    @Override
    public void resume() {
        pauseLock.lock();
        try {
            isPaused = false;
            pausedCond.signalAll();
        } finally {
            pauseLock.unlock();
        }
    }
}
