package org.traffichunter.titan.springframework.stomp;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traffichunter.titan.core.resilience.retry.RetryListener;

/**
 * Default retry listener that records Titan client retry lifecycle events.
 *
 * @author yun
 */
public final class TitanRetryLoggingListener implements RetryListener {

    private static final Logger log = LoggerFactory.getLogger(TitanRetryLoggingListener.class);

    @Override
    public void onRetry(int attempt, Duration delay) {
        log.warn("Retry scheduled attempt #{}, delay={}", attempt, delay);
    }

    @Override
    public void onRetryFailed(int attempt, Throwable cause) {
        log.warn("Retry failed attempt #{}, cause={}", attempt, cause.getMessage());
    }

    @Override
    public void onRetryExhausted(int attempt, Throwable cause) {
        log.warn("Retry exhausted attempt #{}, cause={}", attempt, cause.getMessage());
    }
}
