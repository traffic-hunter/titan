package org.traffichunter.titan.springframework.stomp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.retry.support.RetryTemplate;

class TitanListenerRetryTemplateTest {

    @Test
    void retry_template_uses_listener_retry_properties() {
        TitanProperties properties = new TitanProperties();
        properties.setListenerMaxAttempts(3);
        properties.setListenerInitialBackoffMillis(1L);
        properties.setListenerMaxBackoffMillis(1L);
        properties.setListenerBackoffMultiplier(2.0d);

        TitanListenerConfiguration configuration = new TitanListenerConfiguration();
        RetryTemplate retryTemplate = configuration.titanListenerRetryTemplate(properties);

        AtomicInteger attempts = new AtomicInteger();
        assertThrows(IllegalStateException.class, () -> retryTemplate.execute(context -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("fail");
        }));

        assertEquals(3, attempts.get());
    }
}
