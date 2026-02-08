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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.channel.EventLoop;
import org.traffichunter.titan.core.util.Assert;

/**
 * @author yungwang-o
 */
@Slf4j
public class PromiseImpl<C> implements Promise<C> {

    private final EventLoop eventLoop;

    private boolean isCompleted;
    private @Nullable C result;
    private @Nullable Throwable error;
    private List<AsyncListener> listeners;
    private final @Nullable Callable<C> task;

    private int waiter;
    private boolean isCancelled;

    protected PromiseImpl(EventLoop eventLoop, @Nullable Runnable task) {
        this(eventLoop, Executors.callable(Objects.requireNonNull(task), null));
    }

    protected PromiseImpl(EventLoop eventLoop, @Nullable Callable<C> task) {
        this.eventLoop = eventLoop;
        this.listeners = new ArrayList<>();
        this.task = task;
    }

    @Override
    public void run() {
        if(!eventLoop.inEventLoop()) {
            return;
        }

        try {
            C result = task.call();
            success(result);
        } catch (Exception e) {
            fail(e);
        }
    }

    @Override
    public Promise<C> addListener(final AsyncListener listener) {
        synchronized (this) {
            listeners.add(listener);
        }
        if(isDone()) {
            notifyListeners();
        }
        return this;
    }

    @Override
    public Promise<C> removeListener(final AsyncListener listener) {
        synchronized (this) {
            this.listeners.remove(listener);
        }

        return this;
    }

    @Override
    public Future<C> future() {
        return this;
    }

    @Override
    public boolean isSuccess() {
        return isCompleted && error == null;
    }

    @Override
    public boolean cancel(final boolean mayInterruptIfRunning) {
        if(isCancelled) {
            return true;
        }

        synchronized (this) {
            if(isDone()) {
                return false;
            }

            isCancelled = true;
            error = new CancellationException("Cancelled result");
            isCompleted = true;

            if(waiter > 0) {
                notifyAll();
            }
        }

        notifyListeners();
        return true;
    }

    @Override
    public boolean isCancelled() {
        return isCancelled;
    }

    @Override
    public boolean isDone() {
        return isCompleted;
    }

    @Override
    public C get() throws InterruptedException, ExecutionException {
        await();
        if(error == null) {
            return result;
        }
        if(error instanceof CancellationException) {
            throw (CancellationException) error;
        }

        throw new ExecutionException(error);
    }

    @Override
    public C get(final long timeout, final TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {

        if(!await(timeout, unit).isDone()) {
            throw new TimeoutException("Timed out waiting for " + timeout + " " + unit);
        }
        if(error == null) {
            return result;
        }
        if(error instanceof CancellationException) {
            throw (CancellationException) error;
        }

        throw new ExecutionException(error);
    }

    @Override
    public @Nullable Throwable error() {
        return this.error;
    }

    @Override
    public Promise<C> await() throws InterruptedException {
        if(isDone()) {
            return this;
        }
        if(Thread.interrupted()) {
            throw new InterruptedException();
        }

        synchronized (this) {
            while (!isDone()) {
                waiter++;
                try {
                    wait();
                } finally {
                    waiter--;
                }
            }
        }

        return this;
    }

    @Override
    public Promise<C> await(final long timeout, final TimeUnit timeUnit) throws InterruptedException {
        Assert.check(timeout >= 0, () -> new PromiseException("Timeout more than or equals 0"));

        if(isDone()) {
            return this;
        }
        if(Thread.interrupted()) {
            throw new InterruptedException("Interrupted while waiting for");
        }

        final long timeOutNanos = timeUnit.toNanos(timeout);
        final long deadline = System.nanoTime() + timeOutNanos;
        long remainingNanos = timeOutNanos;

        synchronized (this) {
            while (!isDone()) {
                waiter++;
                try {
                    long timeoutMillis = remainingNanos / 1_000_000;
                    int nanos = (int) (remainingNanos % 1_000_000);

                    wait(timeoutMillis, nanos);

                    remainingNanos = deadline - System.nanoTime();
                    if (remainingNanos <= 0) {
                        break;
                    }
                } finally {
                    waiter--;
                }
            }
        }

        return this;
    }

    @Override
    public Promise<C> complete(@Nullable final C result, @Nullable final Throwable error) {
        if(isDone()) {
            return this;
        }

        synchronized (this) {
            this.result = result;
            this.error = error;
            this.isCompleted = true;

            if(waiter > 0) {
                notifyAll();
            }
        }

        notifyListeners();
        return this;
    }

    private void notifyListeners() {
        if(!eventLoop.inEventLoop()) {
            eventLoop.register(this::notifyListeners);
            return;
        }

        List<AsyncListener> listeners;

        synchronized (this) {
            if(this.listeners.isEmpty()) {
                return;
            }
            listeners = this.listeners;
            this.listeners = new ArrayList<>();
        }
        while (true) {
            for(AsyncListener listener : listeners) {
                try {
                    listener.onComplete(this);
                } catch (Exception e) {
                    log.error("A task terminated with unexpected exception. Exception: ", e);
                }
            }

            synchronized (this) {
                if (this.listeners.isEmpty()) {
                    break;
                }
                listeners = this.listeners;
                this.listeners = new ArrayList<>();
            }
        }
    }
}
