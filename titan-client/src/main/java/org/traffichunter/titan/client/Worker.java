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
package org.traffichunter.titan.client;

import org.jspecify.annotations.NonNull;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Executes client state changes serially, regardless of the transport implementation.
 *
 * <p>A worker runs submitted tasks in order on the same execution context: an {@code EventLoop}
 * for Titan or a fixed {@code Context} for Vert.x. Reconnect and subscription state use this
 * shared execution contract; neither runtime appears in the public client API.</p>
 *
 * <p>Closing a worker prevents further submissions. Ownership of the underlying runtime remains
 * with the driver that supplied the worker.</p>
 *
 * @author yun
 */
public interface Worker extends Executor, AutoCloseable {

    /**
     * Schedules a task on this worker.
     *
     * @param task task to execute serially
     */
    @Override
    void execute(@NonNull Runnable task);

    /**
     * Schedules a task and returns its result as a JDK future.
     *
     * @param task task to execute
     * @param <T> result type
     * @return future completed with the returned value or task failure
     */
    <T> CompletableFuture<T> submit(Callable<T> task);

    /**
     * Schedules an asynchronous operation and flattens its nested future.
     *
     * <p>The callable runs on this worker. The future it returns determines where the
     * asynchronous operation completes.</p>
     *
     * @param task asynchronous operation supplier
     * @param <T> result type
     * @return flattened operation future
     */
    default <T> CompletableFuture<T> thenCompose(Callable<? extends CompletableFuture<T>> task) {
        return submit(task).thenCompose(f -> f);
    }

    /**
     * Returns whether the current thread is executing in this worker's context.
     *
     * @return {@code true} when called from this worker
     */
    boolean inWorker();

    /** Stops accepting work without assuming ownership of an external runtime. */
    @Override
    void close() throws Exception;
}
