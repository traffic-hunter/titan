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

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.channel.EventLoop;

/**
 * Event-loop-backed asynchronous result.
 *
 * <p>A promise is both a {@link RunnableFuture} and a {@link Completable}. Event loops submit
 * promises as runnable tasks, while transport code can also complete them manually when an I/O
 * event happens later. Listener notification is serialized through the owning {@link EventLoop}.
 * Promise callbacks should not run blocking code.</p>
 *
 * @author yungwang-o
 */
public interface Promise<C> extends RunnableFuture<C>, Completable<C> {

    static <C> Promise<C> newPromise(EventLoop eventLoop) {
        return new PromiseImpl<>(eventLoop, () -> {});
    }

    static <C> Promise<C> newPromise(EventLoop eventLoop, @Nullable Runnable task) {
        return new PromiseImpl<>(eventLoop, task);
    }

    static <C> Promise<C> newPromise(EventLoop eventLoop, @Nullable Callable<C> task) {
        return new PromiseImpl<>(eventLoop, task);
    }

    static <C> Promise<C> failedPromise(EventLoop eventLoop, Throwable err) {
        Promise<C> failedPromise = Promise.newPromise(eventLoop, () -> null);
        failedPromise.fail(err);
        return failedPromise;
    }

    /**
     * Returns whether the promise completed without an error.
     */
    boolean isSuccess();

    default boolean cancel() {
        return cancel(false);
    }

    /**
     * @param mayInterruptIfRunning Cancellation does not interrupt underlying task execution.
     */
    @Override
    boolean cancel(boolean mayInterruptIfRunning);

    default boolean isFailed() {
        return !isSuccess();
    }

    /**
     * Registers a listener that will be notified on the owning event loop.
     *
     * <p>Do not run blocking code in the listener.</p>
     */
    @CanIgnoreReturnValue
    Promise<C> addListener(AsyncListener<C> listener);

    @CanIgnoreReturnValue
    Promise<C> removeListener(AsyncListener<C> listener);

    /**
     * Creates a promise that maps this promise's successful result.
     *
     * <p>Do not run blocking code in the mapper.</p>
     */
    @CanIgnoreReturnValue
    <R> Promise<R> map(Function<? super C, ? extends R> mapper);

    @CanIgnoreReturnValue
    Promise<C> await() throws InterruptedException;

    @CanIgnoreReturnValue
    Promise<C> await(long timeout, TimeUnit timeUnit) throws InterruptedException;

    /**
     * Creates a promise that follows the promise returned by the mapper.
     *
     * <p>Do not run blocking code in the mapper.</p>
     */
    @CanIgnoreReturnValue
    <R> Promise<R> thenCompose(Function<? super C, ? extends Promise<R>> mapper);

    /**
     * Registers a callback for successful completion.
     *
     * <p>Do not run blocking code in the callback.</p>
     */
    @CanIgnoreReturnValue
    Promise<C> onSuccess(Consumer<? super C> success);

    /**
     * Registers a callback for failed completion.
     *
     * <p>Do not run blocking code in the callback.</p>
     */
    @CanIgnoreReturnValue
    Promise<C> onFailure(Consumer<? super Throwable> failure);

    Future<C> future();

    boolean isDone();

    /**
     * Returns the completed value without blocking, or {@code null} if no value is available.
     */
    @Nullable C getNow();

    /**
     * Returns the completion failure, or {@code null} when the promise succeeded or is pending.
     */
    @Nullable Throwable error();
}
