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
