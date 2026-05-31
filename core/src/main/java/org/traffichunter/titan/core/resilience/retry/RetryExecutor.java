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
