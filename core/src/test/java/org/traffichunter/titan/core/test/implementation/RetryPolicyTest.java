package org.traffichunter.titan.core.test.implementation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.traffichunter.titan.core.resilience.retry.ExponentialRetryPolicy;
import org.traffichunter.titan.core.resilience.retry.FixedRetryPolicy;
import org.traffichunter.titan.core.resilience.retry.RetryPolicy;

class RetryPolicyTest {

    @Test
    void fixed_policy_returns_same_delay_for_each_attempt() {
        RetryPolicy policy = RetryPolicy.fixed(3, Duration.ofMillis(100));

        assertThat(policy.canRetry(1)).isTrue();
        assertThat(policy.canRetry(3)).isTrue();
        assertThat(policy.canRetry(4)).isFalse();
        assertThat(policy.delay(1)).isEqualTo(Duration.ofMillis(100));
        assertThat(policy.delay(3)).isEqualTo(Duration.ofMillis(100));
    }

    @Test
    void unlimited_attempts_never_exhaust() {
        RetryPolicy policy = RetryPolicy.fixed(RetryPolicy.UNLIMITED_ATTEMPTS, Duration.ofMillis(100));

        assertThat(policy.canRetry(1)).isTrue();
        assertThat(policy.canRetry(10_000)).isTrue();
    }

    @Test
    void exponential_policy_increases_delay_until_max_delay() {
        RetryPolicy policy = RetryPolicy.exponential(
                5,
                Duration.ofMillis(100),
                Duration.ofMillis(1_000),
                2,
                false
        );

        assertThat(policy.delay(1)).isEqualTo(Duration.ofMillis(100));
        assertThat(policy.delay(2)).isEqualTo(Duration.ofMillis(200));
        assertThat(policy.delay(3)).isEqualTo(Duration.ofMillis(400));
        assertThat(policy.delay(4)).isEqualTo(Duration.ofMillis(800));
        assertThat(policy.delay(5)).isEqualTo(Duration.ofMillis(1_000));
    }

    @Test
    void exponential_policy_increases_delay_until_max_delay_with_jitter() {
        RetryPolicy policy = RetryPolicy.exponentialWithJitter(
                5,
                Duration.ofMillis(100),
                Duration.ofMillis(1_000),
                2
        );

        assertThat(policy.delay(1)).isEqualTo(Duration.ofMillis(100));
        assertThat(policy.delay(2)).isBetween(Duration.ofMillis(100), Duration.ofMillis(200));
        assertThat(policy.delay(3)).isBetween(Duration.ofMillis(100), Duration.ofMillis(400));
        assertThat(policy.delay(4)).isBetween(Duration.ofMillis(100), Duration.ofMillis(800));
        assertThat(policy.delay(5)).isBetween(Duration.ofMillis(100), Duration.ofMillis(1_000));
    }

    @Test
    void policies_reject_invalid_values() {
        assertThatThrownBy(() -> RetryPolicy.fixed(0, Duration.ofMillis(100)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxAttempts");
        assertThatThrownBy(() -> RetryPolicy.fixed(1, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("delay");
        assertThatThrownBy(() -> RetryPolicy.exponential(1, Duration.ofMillis(100), Duration.ofMillis(50), 2, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxDelay");
        assertThatThrownBy(() -> RetryPolicy.exponential(1, Duration.ofMillis(100), Duration.ofMillis(100), 1, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("multiplier");
        assertThatThrownBy(() -> RetryPolicy.fixed(1, Duration.ofMillis(100)).delay(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("attempt");
    }

    @Nested
    class BuilderTest {

        @Test
        void fixed_policy_builder_creates_policy_with_configured_values() {
            FixedRetryPolicy policy = FixedRetryPolicy.builder()
                    .maxAttempts(5)
                    .delay(Duration.ofMillis(250))
                    .build();

            assertThat(policy.maxAttempts()).isEqualTo(5);
            assertThat(policy.delay(1)).isEqualTo(Duration.ofMillis(250));
        }

        @Test
        void fixed_policy_builder_supports_unlimited_attempts() {
            FixedRetryPolicy policy = FixedRetryPolicy.builder()
                    .unlimitedAttempts()
                    .delay(Duration.ofMillis(250))
                    .build();

            assertThat(policy.canRetry(1_000)).isTrue();
        }

        @Test
        void exponential_policy_builder_creates_policy_with_configured_values() {
            ExponentialRetryPolicy policy = ExponentialRetryPolicy.builder()
                    .maxAttempts(4)
                    .initialDelay(Duration.ofMillis(100))
                    .maxDelay(Duration.ofMillis(1_000))
                    .multiplier(3)
                    .build();

            assertThat(policy.maxAttempts()).isEqualTo(4);
            assertThat(policy.multiplier()).isEqualTo(3);
            assertThat(policy.maxDelay()).isEqualTo(Duration.ofMillis(1_000));
            assertThat(policy.delay(1)).isEqualTo(Duration.ofMillis(100));
            assertThat(policy.delay(2)).isEqualTo(Duration.ofMillis(300));
            assertThat(policy.delay(3)).isEqualTo(Duration.ofMillis(900));
            assertThat(policy.delay(4)).isEqualTo(Duration.ofMillis(1_000));
        }

        @Test
        void exponential_policy_builder_uses_defaults() {
            ExponentialRetryPolicy policy = ExponentialRetryPolicy.builder().build();

            assertThat(policy.maxAttempts()).isEqualTo(3);
            assertThat(policy.multiplier()).isEqualTo(2);
            assertThat(policy.delay(1)).isEqualTo(Duration.ofSeconds(1));
            assertThat(policy.delay(3)).isEqualTo(Duration.ofSeconds(4));
        }
    }
}
