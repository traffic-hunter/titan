package org.traffichunter.titan.springframework.stomp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.traffichunter.titan.core.resilience.retry.ExponentialRetryPolicy;
import org.traffichunter.titan.core.resilience.retry.FixedRetryPolicy;
import org.traffichunter.titan.core.resilience.retry.RetryPolicy;

class TitanPropertiesTest {

    @Test
    void defaults_are_initialized() {
        TitanProperties properties = new TitanProperties();

        assertTrue(properties.isEnabled());
        assertTrue(properties.isAutoStart());
        assertTrue(properties.isAutoConnect());
        assertEquals("127.0.0.1", properties.getHost());
        assertEquals(61613, properties.getPort());
        assertEquals(30000L, properties.getConnectTimeoutMillis());
        assertTrue(properties.isAutoComputeContentLength());
        assertFalse(properties.isUseStompFrame());
        assertFalse(properties.isBypassHostHeader());
        assertFalse(properties.getRetry().isEnabled());
        assertTrue(properties.getReconnect().isEnabled());
        assertEquals(TitanProperties.Retry.Type.EXP, properties.getRetry().getType());
        assertEquals(3, properties.getRetry().getMaxAttempts());
        assertEquals(Duration.ofSeconds(1), properties.getRetry().getDelay());
        assertEquals(Duration.ofSeconds(30), properties.getRetry().getMaxDelay());
        assertEquals(2, properties.getRetry().getMultiplier());
    }

    @Test
    void set_secondary_threads_fallback_to_available_processors_when_non_positive() {
        TitanProperties properties = new TitanProperties();

        properties.setSecondaryThreads(0);
        assertEquals(Runtime.getRuntime().availableProcessors(), properties.getSecondaryThreads());

        properties.setSecondaryThreads(-1);
        assertEquals(Runtime.getRuntime().availableProcessors(), properties.getSecondaryThreads());
    }

    @Test
    void set_secondary_threads_uses_explicit_value_when_positive() {
        TitanProperties properties = new TitanProperties();

        properties.setSecondaryThreads(4);

        assertEquals(4, properties.getSecondaryThreads());
    }

    @Test
    void retry_properties_create_fixed_retry_policy() {
        TitanProperties properties = new TitanProperties();
        properties.getRetry().setType(TitanProperties.Retry.Type.FIX);
        properties.getRetry().setMaxAttempts(5);
        properties.getRetry().setDelay(Duration.ofMillis(250));

        RetryPolicy policy = properties.getRetry().toPolicy();

        assertTrue(policy instanceof FixedRetryPolicy);
        assertEquals(5, policy.maxAttempts());
        assertEquals(Duration.ofMillis(250), policy.delay(1));
    }

    @Test
    void retry_properties_create_exponential_retry_policy() {
        TitanProperties properties = new TitanProperties();
        properties.getRetry().setMaxAttempts(4);
        properties.getRetry().setDelay(Duration.ofMillis(100));
        properties.getRetry().setMaxDelay(Duration.ofMillis(1_000));
        properties.getRetry().setMultiplier(3);

        RetryPolicy policy = properties.getRetry().toPolicy();

        assertTrue(policy instanceof ExponentialRetryPolicy);
        assertEquals(4, policy.maxAttempts());
        assertEquals(Duration.ofMillis(100), policy.delay(1));
        assertEquals(Duration.ofMillis(300), policy.delay(2));
        assertEquals(Duration.ofMillis(900), policy.delay(3));
        assertEquals(Duration.ofMillis(1_000), policy.delay(4));
    }
}
