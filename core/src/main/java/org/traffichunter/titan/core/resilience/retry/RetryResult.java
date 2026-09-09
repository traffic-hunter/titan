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

/**
 * Handle for a retry sequence scheduled by a {@link RetryExecutor}.
 *
 * <p>Cancellation is best-effort. It cancels the currently scheduled attempt when one
 * exists and prevents future attempts from being scheduled.</p>
 *
 * @author yun
 */
public interface RetryResult {

    static RetryResult noop() {
        return new RetryResult() {
            @Override
            public void cancel() { }
            @Override
            public void cancel(boolean mayInterruptIfRunning) { }
            @Override
            public boolean isCancelled() { return false; }
        };
    }

    /**
     * Cancels the retry sequence without interrupting a running attempt.
     */
    default void cancel() { cancel(false); }

    /**
     * Cancels the retry sequence.
     *
     * @param mayInterruptIfRunning whether a running scheduled task may be interrupted
     */
    void cancel(boolean mayInterruptIfRunning);

    /**
     * Returns whether cancellation has been requested or observed by the scheduled task.
     *
     * @return {@code true} when this retry sequence is cancelled
     */
    boolean isCancelled();
}
