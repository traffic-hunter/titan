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
package org.traffichunter.titan.core.util.concurrent;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;

import org.jspecify.annotations.NonNull;
import org.traffichunter.titan.core.concurrent.Promise;

/**
 * Execution contract for components backed by a Titan-managed execution context.
 *
 * <p>This interface can be supplied to JDK asynchronous APIs that accept an
 * {@link Executor} without exposing the lifecycle or scheduling capabilities of the
 * underlying execution context.</p>
 *
 * @author yun
 */
public interface EventExecutor extends Executor {

    /**
     * Submits a task to the managed execution context.
     *
     * @param task task to execute
     */
    @Override
    void execute(@NonNull Runnable task);

    /**
     * Submits a task and returns its asynchronous completion.
     *
     * @param task task to execute
     * @return promise completed when the task finishes
     */
    Promise<Void> submit(Runnable task);

    /**
     * Submits a value-producing task.
     *
     * @param task task to execute
     * @param <V> result type
     * @return promise completed with the task result
     */
    <V> Promise<V> submit(Callable<V> task);

    /**
     * Creates an incomplete promise whose listeners execute in this execution context.
     */
    default <V> Promise<V> newPromise() {
        return Promise.newPromise(this);
    }

    /**
     * Creates a promise backed by the supplied task.
     */
    default Promise<Void> newPromise(Runnable task) {
        return Promise.newPromise(this, task);
    }

    /**
     * Creates a promise backed by the supplied callable.
     */
    default <V> Promise<V> newPromise(Callable<V> task) {
        return Promise.newPromise(this, task);
    }

    /**
     * Returns whether the caller is running in this execution context.
     */
    default boolean inEventLoop() {
        return inEventLoop(Thread.currentThread());
    }

    /**
     * Returns whether the given thread belongs to this execution context.
     */
    boolean inEventLoop(Thread thread);
}
