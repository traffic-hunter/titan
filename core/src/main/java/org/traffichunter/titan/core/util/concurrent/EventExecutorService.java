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

import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Execution contract for components backed by a Titan-managed execution context.
 *
 * <p>This interface preserves Titan's {@link Promise} return types while exposing the
 * standard JDK scheduling and lifecycle contract.</p>
 *
 * @author yun
 */
public interface EventExecutorService extends ScheduledExecutorService {

    /**
     * Submits a task to the managed execution context.
     *
     * @param task task to execute
     */
    @Override
    void execute(Runnable task);

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

    @Override
    default <V> Promise<V> submit(Runnable task, V result) {
        return submit(Executors.callable(task, result));
    }

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
