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

import com.google.errorprone.annotations.CanIgnoreReturnValue;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;
/**
 * Event-loop-backed asynchronous result.
 *
 * <p>A promise is both a {@link RunnableFuture} and a {@link Completable}. Event loops submit
 * promises as runnable tasks, while transport code can also complete them manually when an I/O
 * event happens later. Listener notification is serialized through the owning {@link EventExecutorService}.
 * Promise callbacks should not run blocking code.</p>
 *
 * @author yungwang-o
 */
public interface Promise<C> extends RunnableFuture<C>, Completable<C> {

    static <C> Promise<C> newPromise(EventExecutorService executor) {
        return new PromiseImpl<>(executor, () -> {});
    }

    static <C> Promise<C> newPromise(EventExecutorService executor, @Nullable Runnable task) {
        return new PromiseImpl<>(executor, task);
    }

    static <C> Promise<C> newPromise(EventExecutorService executor, @Nullable Callable<C> task) {
        return new PromiseImpl<>(executor, task);
    }

    static <C> Promise<C> failedPromise(EventExecutorService executor, Throwable err) {
        Promise<C> failedPromise = Promise.newPromise(executor, () -> null);
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
        return isDone() && !isSuccess();
    }

    /**
     * Registers a listener that will be notified on the owning event loop.
     *
     * <p>Do not run blocking code in the listener.</p>
     *
     * <p>If the executor rejects notification, the rejection is logged and the listener is not
     * invoked on the caller thread. The completed result remains available through this promise;
     * callback delivery after executor shutdown is not guaranteed.</p>
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
    Promise<C> onSuccess(Consumer<? super @Nullable C> success);

    /**
     * Registers a callback for failed completion.
     *
     * <p>Do not run blocking code in the callback.</p>
     */
    @CanIgnoreReturnValue
    Promise<C> onFailure(Consumer<? super Throwable> failure);

    Future<C> future();

    /**
     * Convert to CompletableFuture
     * @return CompletableFuture
     */
    default CompletableFuture<@Nullable C> toCompletableFuture() {
        CompletableFuture<@Nullable C> completableFuture = new CompletableFuture<>();
        addListener(future -> {
            if (future.isSuccess()) {
                completableFuture.complete(future.getNow());
            } else {
                Throwable error = future.error();
                completableFuture.completeExceptionally(
                        error != null ? error : new PromiseException("Promise failed without error")
                );
            }
        });

        return completableFuture;
    }

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
