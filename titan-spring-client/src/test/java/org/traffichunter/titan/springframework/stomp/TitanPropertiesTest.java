package org.traffichunter.titan.springframework.stomp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TitanPropertiesTest {

    @Test
    void defaults_are_initialized() {
        TitanProperties properties = new TitanProperties();

        assertTrue(properties.isEnabled());
        assertTrue(properties.isAutoStart());
        assertTrue(properties.isAutoConnect());
        assertEquals(TitanProperties.Client.TITAN, properties.getClient());
        assertNull(properties.getEndpoint());
        assertEquals(TitanProperties.Transport.TCP, properties.getTransport());
        assertEquals("/stomp", properties.getWebsocketPath());
        assertNull(properties.getSsl().getBundle());
        assertTrue(properties.getSsl().isVerifyHostname());
        assertEquals("127.0.0.1", properties.getHost());
        assertEquals(61613, properties.getPort());
        assertEquals(5000L, properties.getConnectTimeoutMillis());
        assertTrue(properties.isAutoComputeContentLength());
        assertFalse(properties.isUseStompFrame());
        assertFalse(properties.isBypassHostHeader());
    }

    @Test
    void set_worker_fallback_to_available_processors_when_non_positive() {
        TitanProperties properties = new TitanProperties();

        properties.setWorker(0);
        assertEquals(Runtime.getRuntime().availableProcessors(), properties.getWorker());

        properties.setWorker(-1);
        assertEquals(Runtime.getRuntime().availableProcessors(), properties.getWorker());
    }

    @Test
    void set_worker_uses_explicit_value_when_positive() {
        TitanProperties properties = new TitanProperties();

        properties.setWorker(4);

        assertEquals(4, properties.getWorker());
    }

    @Test
    void configure_spring_ssl_bundle() {
        TitanProperties properties = new TitanProperties();

        properties.getSsl().setBundle("titan-client");
        properties.getSsl().setVerifyHostname(false);

        assertEquals("titan-client", properties.getSsl().getBundle());
        assertFalse(properties.getSsl().isVerifyHostname());
    }
}
