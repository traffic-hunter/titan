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
import java.util.concurrent.ThreadLocalRandom;

/**
 * Retry policy that increases delay exponentially and caps it at a maximum delay.
 *
 * <p>Attempt {@code 1} uses {@code initialDelay}. Each later attempt multiplies
 * the previous delay by {@code multiplier} until {@code maxDelay} is reached.</p>
 *
 * @param maxAttempts maximum number of attempts, or {@link RetryPolicy#UNLIMITED_ATTEMPTS}
 * @param initialDelay positive delay used for attempt {@code 1}
 * @param maxDelay maximum delay returned by this policy
 * @param multiplier multiplier applied between attempts
 *
 * @author yun
 */
public record ExponentialRetryPolicy(
        int maxAttempts,
        Duration initialDelay,
        Duration maxDelay,
        int multiplier,
        boolean jitter
) implements RetryPolicy {

    private static final int DEFAULT_MAX_ATTEMPTS = 3;
    private static final Duration DEFAULT_INITIAL_DELAY = Duration.ofSeconds(1);
    private static final Duration DEFAULT_MAX_DELAY = Duration.ofSeconds(30);
    private static final int DEFAULT_MULTIPLIER = 2;

    private static final ThreadLocalRandom RANDOM = ThreadLocalRandom.current();

    public ExponentialRetryPolicy {
        RetryPolicy.validateMaxAttempts(maxAttempts);
        RetryPolicy.validateDelay(initialDelay);
        RetryPolicy.validateDelay(maxDelay);

        if (maxDelay.compareTo(initialDelay) < 0) {
            throw new IllegalArgumentException("maxDelay must be greater than or equal to initialDelay");
        }
        if (multiplier < 2) {
            throw new IllegalArgumentException("multiplier must be greater than or equal to 2");
        }
    }

    @Override
    public Duration delay(int attempt) {
        if (attempt < 1) {
            throw new IllegalArgumentException("attempt must be greater than zero");
        }

        Duration delay = initialDelay;
        for (int i = 1; i < attempt; i++) {
            delay = multiply(delay, multiplier);
            if (delay.compareTo(maxDelay) >= 0) {
                return maxDelay;
            }
        }

        if (jitter) {
            return jitter(initialDelay, delay);
        }
        return delay;
    }

    public static Builder builder() {
        return new Builder();
    }

    private static Duration jitter(Duration initialDelay, Duration delay) {
        long min = initialDelay.toMillis();
        long max = delay.toMillis();
        if (min >= max) {
            return delay;
        }

        long jitter = RANDOM.nextLong(min, max + 1);
        return Duration.ofMillis(jitter);
    }

    private static Duration multiply(Duration delay, int multiplier) {
        try {
            return delay.multipliedBy(multiplier);
        } catch (ArithmeticException e) {
            return Duration.ofMillis(Long.MAX_VALUE); // Prevent numeric overflow.
        }
    }

    /**
     * Builder for {@link ExponentialRetryPolicy}.
     */
    public static final class Builder {

        private int maxAttempts = DEFAULT_MAX_ATTEMPTS;
        private Duration initialDelay = DEFAULT_INITIAL_DELAY;
        private Duration maxDelay = DEFAULT_MAX_DELAY;
        private int multiplier = DEFAULT_MULTIPLIER;
        private boolean jitter = false;

        private Builder() {
        }

        public Builder maxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
            return this;
        }

        public Builder unlimitedAttempts() {
            this.maxAttempts = RetryPolicy.UNLIMITED_ATTEMPTS;
            return this;
        }

        public Builder initialDelay(Duration initialDelay) {
            this.initialDelay = initialDelay;
            return this;
        }

        public Builder maxDelay(Duration maxDelay) {
            this.maxDelay = maxDelay;
            return this;
        }

        public Builder multiplier(int multiplier) {
            this.multiplier = multiplier;
            return this;
        }

        public Builder jitter(boolean jitter) {
            this.jitter = jitter;
            return this;
        }

        public ExponentialRetryPolicy build() {
            return new ExponentialRetryPolicy(maxAttempts, initialDelay, maxDelay, multiplier, jitter);
        }
    }
}
