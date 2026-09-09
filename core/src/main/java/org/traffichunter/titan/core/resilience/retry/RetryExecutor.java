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
package org.traffichunter.titan.core.resilience.retry;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * Schedules retry attempts for a callback.
 *
 * <p>The executor does not run the callback immediately. Calling {@link #retry(Runnable)}
 * or {@link #retry(Callable)} schedules attempt {@code 1} after the delay returned by
 * the configured {@link RetryPolicy}. If an attempt completes normally, retrying stops.
 * If an attempt throws an exception, the executor schedules the next attempt while the
 * policy still allows it.</p>
 *
 * @author yun
 */
public interface RetryExecutor {

    /**
     * Schedules a retryable runnable.
     *
     * @param callback callback to invoke on each attempt
     * @return handle that can cancel the currently scheduled retry attempt
     */
    RetryResult retry(Runnable callback);

    /**
     * Schedules a retryable callable.
     *
     * <p>The returned value is intentionally ignored. This executor only models retry
     * scheduling and cancellation; consumers that need a result should capture it in
     * their own state or future abstraction.</p>
     *
     * @param callback callback to invoke on each attempt
     * @return handle that can cancel the currently scheduled retry attempt
     */
    <T> RetryResult retry(Callable<T> callback);

    /**
     * Shuts down executor resources using the default timeout.
     */
    default void shutdown() {
        shutdown(30, TimeUnit.SECONDS);
    }

    /**
     * Shuts down executor resources.
     *
     * @param timeout maximum time to wait for shutdown
     * @param timeUnit timeout unit
     */
    void shutdown(long timeout, TimeUnit timeUnit);
}
