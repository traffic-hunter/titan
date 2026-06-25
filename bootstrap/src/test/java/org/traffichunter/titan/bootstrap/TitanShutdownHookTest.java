package org.traffichunter.titan.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class TitanShutdownHookTest {

    @Test
    void run_ignores_callbacks_when_disabled() {
        TitanShutdownHook shutdownHook = new TitanShutdownHook();
        AtomicInteger calls = new AtomicInteger();

        shutdownHook.addShutdownCallback(calls::incrementAndGet);
        shutdownHook.run();

        assertThat(calls).hasValue(0);
    }

    @Test
    void run_executes_registered_callbacks_when_enabled() {
        TitanShutdownHook shutdownHook = new TitanShutdownHook();
        AtomicInteger calls = new AtomicInteger();

        shutdownHook.enableShutdown();
        shutdownHook.addShutdownCallback(calls::incrementAndGet);
        shutdownHook.addShutdownCallback(calls::incrementAndGet);
        shutdownHook.run();

        assertThat(calls).hasValue(2);
    }

    @Test
    void run_continues_after_callback_failure() {
        TitanShutdownHook shutdownHook = new TitanShutdownHook();
        AtomicInteger calls = new AtomicInteger();

        shutdownHook.enableShutdown();
        shutdownHook.addShutdownCallback(() -> {
            throw new IllegalStateException("boom");
        });
        shutdownHook.addShutdownCallback(calls::incrementAndGet);
        shutdownHook.run();

        assertThat(calls).hasValue(1);
    }
}
