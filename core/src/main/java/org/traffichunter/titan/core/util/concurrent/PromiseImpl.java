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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.traffichunter.titan.core.event.EventLoop;
import org.traffichunter.titan.core.util.Assert;

/**
 * @author yungwang-o
 */
@Slf4j
public class PromiseImpl<C> implements Promise<C> {

    private final EventLoop eventLoop;

    private volatile C result;
    private volatile Throwable error;
    private List<AsyncListener> listeners;
    private final Callable<C> task;

    @SuppressWarnings("unchecked")
    PromiseImpl(final EventLoop eventLoop, final Runnable task) {
        this.eventLoop = eventLoop;
        this.listeners = new ArrayList<>();

        if(result == null) {
            this.task = (Callable<C>) Executors.callable(task);
        } else {
            this.task = Executors.callable(task, result);
        }
    }

    PromiseImpl(final EventLoop eventLoop, final Callable<C> task) {
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
        Assert.checkNull(listener, "listener is null");

        synchronized (this) {
            listeners.add(listener);
        }
        if(isDone()) {
            notifyListeners();
        }
        return this;
    }

    @Override
    public final Promise<C> addListeners(final AsyncListener... listeners) {
        Assert.checkNull(listeners, "listeners is null");

        synchronized (this) {
            this.listeners.addAll(Arrays.stream(listeners).toList());
        }
        if(isDone()) {
            notifyListeners();
        }
        return this;
    }

    @Override
    public Promise<C> removeListener(final AsyncListener listener) {
        Assert.checkNull(listener, "listener is null");

        synchronized (this) {
            this.listeners.remove(listener);
        }
        return this;
    }

    @Override
    public final Promise<C> removeListeners(final AsyncListener... listeners) {
        Assert.checkNull(listeners, "listeners is null");

        synchronized (this) {
            this.listeners.removeAll(Arrays.stream(listeners).toList());
        }
        return this;
    }

    @Override
    public Future<C> future() {
        return this;
    }

    @Override
    public boolean isSuccess() {
        return result != null;
    }

    @Override
    public boolean isCancellable() {
        return false;
    }

    @Override
    public boolean cancel(final boolean mayInterruptIfRunning) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isCancelled() {
        return false;
    }

    @Override
    public boolean isDone() {
        return result != null || error != null;
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
    public C get(final long timeout, final TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
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
    public Throwable error() {
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
                wait();
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
            throw new InterruptedException();
        }

        final long timeOutNanos = timeUnit.toNanos(timeout);
        final long deadline = System.nanoTime() + timeOutNanos;
        long remainingNanos = timeOutNanos;

        synchronized (this) {
            while (!isDone()) {
                long timeoutMillis = remainingNanos / 1_000_000;
                int nanos = (int) (remainingNanos % 1_000_000);

                wait(timeoutMillis, nanos);

                remainingNanos = deadline - System.nanoTime();
                if(remainingNanos <= 0) {
                    break;
                }
            }
        }

        return this;
    }

    @Override
    public Promise<C> complete(final C result, final Throwable error) {
        if(isDone()) {
            throw new IllegalStateException("Already completed task");
        }

        synchronized (this) {
            this.result = result;
            this.error = error;
            notifyAll();
            notifyListeners();
        }

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
