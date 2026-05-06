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
import java.util.function.Consumer;
import java.util.function.Function;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.channel.EventLoop;
import org.traffichunter.titan.core.util.Assert;

/**
 * Default {@link Promise} implementation.
 *
 * <p>The implementation is synchronized around completion state and waiters, but listener
 * execution is always redirected to the owning {@link EventLoop}. This keeps callbacks in
 * the same thread-affinity model as channel I/O and avoids running transport callbacks on
 * arbitrary caller threads.</p>
 *
 * @author yungwang-o
 */
@Slf4j
public class PromiseImpl<C> implements Promise<C> {

    protected final EventLoop eventLoop;

    private boolean isCompleted;
    private @Nullable C result;
    private @Nullable Throwable error;
    private List<AsyncListener<C>> listeners;
    protected final @Nullable Callable<C> task;

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
            if (task == null) {
                success(null);
                return;
            }
            C result = task.call();
            success(result);
        } catch (Exception e) {
            fail(e);
        }
    }

    @Override
    public Promise<C> addListener(final AsyncListener<C> listener) {
        synchronized (this) {
            listeners.add(listener);
        }
        if(isDone()) {
            notifyListeners();
        }
        return this;
    }

    @Override
    public Promise<C> removeListener(final AsyncListener<C> listener) {
        synchronized (this) {
            this.listeners.remove(listener);
        }

        return this;
    }

    @Override
    public <R> Promise<R> map(Function<? super @Nullable C, ? extends R> mapper) {
        Promise<R> next = Promise.newPromise(eventLoop);
        addListener(promise -> {
            if (!promise.isSuccess()) {
                next.fail(error != null ? error : new PromiseException("Promise failed without error"));
                return;
            }

            try {
                next.success(mapper.apply(promise.getNow()));
            } catch (Exception e) {
                next.fail(e);
            }
        });
        return next;
    }

    @Override
    public <R> Promise<R> thenCompose(Function<? super @Nullable C, ? extends @Nullable Promise<R>> mapper) {
        Promise<R> next = Promise.newPromise(eventLoop);

        addListener(promise -> {
            if (!promise.isSuccess()) {
                next.fail(error != null ? error : new PromiseException("Promise failed without error"));
                return;
            }

            try {
                Promise<R> mapped = mapper.apply(promise.getNow());
                if (mapped == null) {
                    next.fail(new PromiseException("Composed promise cannot be null"));
                    return;
                }

                mapped.addListener(result -> {
                    if (result.isSuccess()) {
                        next.success(result.getNow());
                        return;
                    }

                    Throwable error = result.error();
                    next.fail(error != null ? error : new PromiseException("Composed promise failed without error"));
                });
            } catch (Exception e) {
                next.fail(e);
            }
        });

        return next;
    }

    @Override
    public Promise<C> onSuccess(Consumer<? super C> success) {
        addListener(promise -> {
            if (promise.isSuccess()) {
                success.accept(promise.getNow());
            }
        });

        return this;
    }

    @Override
    public Promise<C> onFailure(Consumer<? super Throwable> failure) {
        addListener(promise -> {
            if(promise.isFailed()) {
                Throwable error = promise.error();
                if(error != null) {
                    failure.accept(error);
                }
            }
        });

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
        synchronized (this) {
            if(isCancelled) {
                return true;
            }
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
    public @Nullable C getNow() {
        return result;
    }

    @Override
    public @Nullable C get() throws InterruptedException, ExecutionException {
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
    public @Nullable C get(final long timeout, final TimeUnit unit)
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
        tryComplete(result, error);
        return this;
    }

    @Override
    public boolean tryComplete(@Nullable final C result, @Nullable final Throwable error) {
        synchronized (this) {
            if(isDone()) {
                return false;
            }

            this.result = result;
            this.error = error;
            this.isCompleted = true;

            if(waiter > 0) {
                notifyAll();
            }
        }

        notifyListeners();
        return true;
    }

    private void notifyListeners() {
        if(!eventLoop.inEventLoop()) {
            eventLoop.register(this::notifyListeners);
            return;
        }

        List<AsyncListener<C>> listeners;

        synchronized (this) {
            if(this.listeners.isEmpty()) {
                return;
            }
            listeners = this.listeners;
            this.listeners = new ArrayList<>();
        }
        while (true) {
            for(AsyncListener<C> listener : listeners) {
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
