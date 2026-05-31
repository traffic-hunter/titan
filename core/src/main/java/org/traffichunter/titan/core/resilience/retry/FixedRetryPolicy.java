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
 * Retry policy that returns the same delay for every retry attempt.
 *
 * @author yun
 */
public record FixedRetryPolicy(
        int maxAttempts,
        Duration delay
) implements RetryPolicy {

    private static final int DEFAULT_MAX_ATTEMPTS = 3;
    private static final Duration DEFAULT_DELAY = Duration.ofSeconds(1);

    public FixedRetryPolicy {
        validateMaxAttempts(maxAttempts);
        validateDelay(delay);
    }

    @Override
    public Duration delay(int attempt) {
        if (attempt < 1) {
            throw new IllegalArgumentException("attempt must be greater than zero");
        }
        return delay;
    }

    public static Builder builder() {
        return new Builder();
    }

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

    public static final class Builder {

        private int maxAttempts = DEFAULT_MAX_ATTEMPTS;
        private Duration delay = DEFAULT_DELAY;

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

        public Builder delay(Duration delay) {
            this.delay = delay;
            return this;
        }

        public FixedRetryPolicy build() {
            return new FixedRetryPolicy(maxAttempts, delay);
        }
    }
}
