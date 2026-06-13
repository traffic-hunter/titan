package org.traffichunter.titan.springframework.stomp;

import java.util.function.Supplier;
import org.traffichunter.titan.core.resilience.retry.RetryListener;

/**
 * Updates the client lifecycle state when reconnect attempts are exhausted.
 *
 * @author yun
 */
public final class TitanReconnectStateRetryListener implements RetryListener {

    private final Supplier<TitanClientManager> clientManager;

    public TitanReconnectStateRetryListener(Supplier<TitanClientManager> clientManager) {
        this.clientManager = clientManager;
    }

    @Override
    public void onRetryExhausted(int attempt, Throwable cause) {
        clientManager.get().reconnectExhausted();
    }
}
