package org.traffichunter.titan.springframework.stomp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

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
        assertEquals(5, properties.getListenerMaxAttempts());
        assertEquals(1000L, properties.getListenerInitialBackoffMillis());
        assertEquals(30000L, properties.getListenerMaxBackoffMillis());
        assertEquals(2.0d, properties.getListenerBackoffMultiplier());
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
    void listener_retry_properties_apply_floor_values() {
        TitanProperties properties = new TitanProperties();

        properties.setListenerMaxAttempts(0);
        properties.setListenerInitialBackoffMillis(0);
        properties.setListenerMaxBackoffMillis(0);
        properties.setListenerBackoffMultiplier(1.0d);

        assertEquals(1, properties.getListenerMaxAttempts());
        assertEquals(1L, properties.getListenerInitialBackoffMillis());
        assertEquals(1L, properties.getListenerMaxBackoffMillis());
        assertEquals(2.0d, properties.getListenerBackoffMultiplier());
    }
}
