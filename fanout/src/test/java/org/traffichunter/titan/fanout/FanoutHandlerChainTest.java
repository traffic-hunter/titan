package org.traffichunter.titan.fanout;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;
import org.traffichunter.titan.core.message.Message;
import org.traffichunter.titan.core.resilience.backup.BackupOption;
import org.traffichunter.titan.core.resilience.backup.DestinationBackupSystem;
import org.traffichunter.titan.core.util.Destination;
import org.traffichunter.titan.core.util.buffer.Buffer;

class FanoutHandlerChainTest {

    @TempDir
    Path tempDir;

    @Test
    void chain_runs_handlers_in_order() {
        List<String> calls = new ArrayList<>();
        FanoutHandlerChain chain = new FanoutHandlerChain()
                .add((context, next) -> {
                    calls.add("first");
                    return next.next(context);
                })
                .add((context, next) -> {
                    calls.add("second");
                    return next.next(context);
                });

        chain.next(new FanoutContext(message("/queue/chain-order"))).join();

        assertThat(calls).containsExactly("first", "second");
    }

    @Test
    void route_handler_sets_routed_message_before_next_handler() {
        Message message = message("/queue/route");
        FanoutHandlerChain chain = new FanoutHandlerChain(List.of(
                new RouteFanoutHandler(ignored -> message),
                (context, next) -> {
                    assertThat(context.getRoutedMessage()).isSameAs(message);
                    return next.next(context);
                }
        ));

        chain.next(new FanoutContext(message)).join();
    }

    @Test
    void dispatch_handler_ignores_unrouted_message() {
        AtomicInteger fanoutCount = new AtomicInteger();
        FanoutHandlerChain chain = new FanoutHandlerChain(List.of(
                new DispatchFanoutHandler(ignored -> fanoutCount.incrementAndGet())
        ));

        chain.next(new FanoutContext(message("/queue/no-route"))).join();

        assertThat(fanoutCount).hasValue(0);
    }

    @Test
    void dispatch_handler_uses_routed_destination() {
        AtomicInteger fanoutCount = new AtomicInteger();
        Message message = message("/queue/fanout");
        FanoutContext context = new FanoutContext(message);
        context.setRoutedMessage(message);
        FanoutHandlerChain chain = new FanoutHandlerChain(List.of(
                new DispatchFanoutHandler(destination -> {
                    assertThat(destination).isEqualTo(message.getDestination());
                    fanoutCount.incrementAndGet();
                })
        ));

        chain.next(context).join();

        assertThat(fanoutCount).hasValue(1);
    }

    @Test
    void chain_runs_handlers_on_supplied_executor() {
        try (var executor = Executors.newSingleThreadExecutor()) {
            Message message = message("/queue/async-route");
            Thread callerThread = Thread.currentThread();
            AtomicReference<Thread> handlerThread = new AtomicReference<>();
            FanoutHandlerChain chain = new FanoutHandlerChain(executor, List.of(
                    new RouteFanoutHandler(ignored -> {
                        handlerThread.set(Thread.currentThread());
                        return message;
                    })
            ));

            CompletableFuture<?> future = chain.next(new FanoutContext(message));

            future.join();
            assertThat(handlerThread.get()).isNotSameAs(callerThread);
        }
    }

    @Test
    void middle_chain_can_add_backup_between_route_and_dispatch() throws Exception {
        DestinationBackupSystem backupSystem = new DestinationBackupSystem(
                tempDir,
                BackupOption.fromConfig("aof", "no", null)
        );
        Message message = message("/queue/backup-chain");
        AtomicInteger fanoutCount = new AtomicInteger();
        FanoutHandlerChain middleHandlers = FanoutHandlerChain.chain()
                .add(new BackupFanoutHandler(backupSystem));
        FanoutHandlerChain.chain()
                .add(new RouteFanoutHandler(ignored -> message))
                .addAll(middleHandlers)
                .add(new DispatchFanoutHandler(ignored -> fanoutCount.incrementAndGet()))
                .next(new FanoutContext(message))
                .join();

        assertThat(fanoutCount).hasValue(1);
        assertThat(backupSystem.inspect("/queue/backup-chain")).isTrue();

        backupSystem.close();
    }

    private static Message message(String destination) {
        return Message.builder()
                .destination(Destination.create(destination))
                .createdAt(Instant.now())
                .producerId("test")
                .body(Buffer.alloc("test".getBytes()))
                .build();
    }
}
