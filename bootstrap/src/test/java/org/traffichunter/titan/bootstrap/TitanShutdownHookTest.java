package org.traffichunter.titan.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
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
    void run_orders_callbacks_and_preserves_registration_order_within_a_phase() {
        TitanShutdownHook shutdownHook = new TitanShutdownHook();
        List<String> calls = new ArrayList<>();

        shutdownHook.enableShutdown();
        shutdownHook.addShutdownCallback(GlobalShutdownHook.RUNTIME_ORDER, () -> calls.add("runtime"));
        shutdownHook.addShutdownCallback(GlobalShutdownHook.DISPATCH_ORDER, () -> calls.add("dispatch-1"));
        shutdownHook.addShutdownCallback(GlobalShutdownHook.SERVER_ORDER, () -> calls.add("server"));
        shutdownHook.addShutdownCallback(GlobalShutdownHook.DISPATCH_ORDER, () -> calls.add("dispatch-2"));
        shutdownHook.addShutdownCallback(() -> calls.add("default"));
        shutdownHook.run();

        assertThat(calls).containsExactly("dispatch-1", "dispatch-2", "server", "runtime", "default");
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
