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
 * Retry policy that returns the same delay for every retry attempt.
 *
 * @param maxAttempts maximum number of attempts, or {@link RetryPolicy#UNLIMITED_ATTEMPTS}
 * @param delay positive delay used for every attempt
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
        RetryPolicy.validateMaxAttempts(maxAttempts);
        RetryPolicy.validateDelay(delay);
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

    /**
     * Builder for {@link FixedRetryPolicy}.
     */
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
