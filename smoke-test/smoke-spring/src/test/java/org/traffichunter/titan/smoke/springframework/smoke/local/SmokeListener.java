package org.traffichunter.titan.smoke.springframework.smoke.local;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.springframework.boot.test.context.TestComponent;
import org.traffichunter.titan.springframework.stomp.annotation.TitanListener;

@TestComponent
final class SmokeListener {

    static final String DESTINATION = "/queue/smoke-local/listener";

    private final BlockingQueue<String> received = new LinkedBlockingQueue<>();

    @TitanListener(destination = DESTINATION)
    public void onMessage(String payload) {
        received.add(payload);
    }

    void clear() {
        received.clear();
    }

    BlockingQueue<String> received() {
        return received;
    }
}
