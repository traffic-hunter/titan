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
package org.traffichunter.titan.core.util.concurrent;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.traffichunter.titan.bootstrap.Configurations;

/**
 * @author yungwang-o
 */
public class AdvancedThreadPoolExecutor extends ThreadPoolExecutor implements Pausable {

    private final Runnable NOOP = () -> { };

    private final Lock pauseLock = new ReentrantLock();

    private final Condition pausedCond = pauseLock.newCondition();

    private boolean isPaused = false;

    public AdvancedThreadPoolExecutor(final int corePoolSize,
                                      final int maximumPoolSize,
                                      final long keepAliveTime,
                                      final TimeUnit unit,
                                      final BlockingQueue<Runnable> workQueue) {

        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
    }

    public AdvancedThreadPoolExecutor(final int corePoolSize,
                                      final int maximumPoolSize,
                                      final long keepAliveTime,
                                      final TimeUnit unit,
                                      final BlockingQueue<Runnable> workQueue,
                                      final ThreadFactory threadFactory) {

        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory);
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
