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
 * Retry delay policy shared by transport and integration modules.
 *
 * <p>Attempts are one-based. The first retry attempt is {@code 1}, the second
 * retry attempt is {@code 2}, and so on.</p>
 *
 * @author yun
 */
public interface RetryPolicy {

    static void validateMaxAttempts(int maxAttempts) {
        if (maxAttempts == RetryPolicy.UNLIMITED_ATTEMPTS || maxAttempts > 0) {
            return;
        }
        throw new IllegalArgumentException("maxAttempts must be positive or UNLIMITED_ATTEMPTS");
    }

    static void validateDelay(Duration delay) {
        if (delay.isNegative() || delay.isZero()) {
            throw new IllegalArgumentException("delay must be positive");
        }
    }

    /**
     * Sentinel value for policies that never stop by attempt count.
     */
    int UNLIMITED_ATTEMPTS = -1;

    /**
     * Creates a policy that uses the same delay for every attempt.
     *
     * @param maxAttempts maximum number of attempts, or {@link #UNLIMITED_ATTEMPTS}
     * @param delay delay used for every attempt
     * @return fixed retry policy
     */
    static RetryPolicy fixed(int maxAttempts, Duration delay) {
        return new FixedRetryPolicy(maxAttempts, delay);
    }

    static RetryPolicy exponentialWithJitter(
            int maxAttempts,
            Duration initialDelay,
            Duration maxDelay,
            int multiplier
    ) {
        return exponential(maxAttempts, initialDelay, maxDelay, multiplier, true);
    }

    /**
     * Creates a policy that multiplies the previous delay until a maximum delay is reached.
     *
     * @param maxAttempts maximum number of attempts, or {@link #UNLIMITED_ATTEMPTS}
     * @param initialDelay delay used for attempt {@code 1}
     * @param maxDelay upper bound for calculated delays
     * @param multiplier multiplier applied between attempts
     * @param jitter whether to add random jitter to delays
     * @return exponential retry policy
     */
    static RetryPolicy exponential(
            int maxAttempts,
            Duration initialDelay,
            Duration maxDelay,
            int multiplier,
            boolean jitter
    ) {
        return new ExponentialRetryPolicy(maxAttempts, initialDelay, maxDelay, multiplier, jitter);
    }

    /**
     * Returns the maximum number of attempts allowed by this policy.
     *
     * @return positive attempt count, or {@link #UNLIMITED_ATTEMPTS}
     */
    int maxAttempts();

    /**
     * Returns the delay before the given attempt is executed.
     *
     * @param attempt one-based retry attempt number
     * @return delay for the attempt
     * @throws IllegalArgumentException when {@code attempt} is less than {@code 1}
     */
    Duration delay(int attempt);

    /**
     * Returns whether the given attempt is allowed by this policy.
     *
     * @param attempt one-based retry attempt number
     * @return {@code true} when the attempt can be scheduled
     * @throws IllegalArgumentException when {@code attempt} is less than {@code 1}
     */
    default boolean canRetry(int attempt) {
        if (attempt < 1) {
            throw new IllegalArgumentException("attempt must be greater than zero");
        }

        int maxAttempts = maxAttempts();
        return maxAttempts == UNLIMITED_ATTEMPTS || attempt <= maxAttempts;
    }
}
