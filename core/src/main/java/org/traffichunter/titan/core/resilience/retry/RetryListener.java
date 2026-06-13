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

import java.time.Duration;

/**
 * Observer callbacks for retry scheduling and failures.
 *
 * <p>Listeners are called from the executor thread that owns the retry attempt.
 * Implementations should avoid blocking work and should not throw exceptions.</p>
 *
 * @author yun
 */
public interface RetryListener {

    /**
     * Listener that ignores every retry event.
     */
    RetryListener NOOP = new RetryListener() {
    };

    /**
     * Called when an attempt is accepted by the policy and scheduled.
     *
     * @param attempt one-based retry attempt number
     * @param delay delay before the attempt is executed
     */
    default void onRetry(int attempt, Duration delay) {
    }

    /**
     * Called after an attempt throws an exception.
     *
     * @param attempt one-based retry attempt number
     * @param cause exception thrown by the callback
     */
    default void onRetryFailed(int attempt, Throwable cause) {
    }

}
