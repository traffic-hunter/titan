/*
The MIT License

Copyright (c) 2025 traffic-hunter

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
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
