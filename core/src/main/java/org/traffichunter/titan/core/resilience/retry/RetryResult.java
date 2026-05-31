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

/**
 * Handle for a retry sequence scheduled by a {@link RetryExecutor}.
 *
 * <p>Cancellation is best-effort. It cancels the currently scheduled attempt when one
 * exists and prevents future attempts from being scheduled.</p>
 *
 * @author yun
 */
public interface RetryResult {

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
