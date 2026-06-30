package org.traffichunter.titan.fanout;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.traffichunter.titan.core.message.Message;
import org.traffichunter.titan.core.util.Destination;
import org.traffichunter.titan.core.util.buffer.Buffer;

class DispatchChainHandlerChainTest {

    @Test
    void chain_runs_handlers_in_order() {
        List<String> calls = new ArrayList<>();
        DispatchHandlerChain chain = new DispatchHandlerChain()
                .add((context, next) -> {
                    calls.add("first");
                    return next.sparkChainHandler(context);
                })
                .add((context, next) -> {
                    calls.add("second");
                    return next.sparkChainHandler(context);
                });

        chain.sparkChainHandler(new DispatchContext(message("/queue/chain-order"))).join();

        assertThat(calls).containsExactly("first", "second");
    }

    @Test
    void route_handler_sets_routed_message_before_next_handler() {
        Message message = message("/queue/route");
        DispatchHandlerChain chain = new DispatchHandlerChain(List.of(
                new RouteDispatchChainHandler(ignored -> message),
                (context, next) -> {
                    assertThat(context.getRoutedMessage()).isSameAs(message);
                    return next.sparkChainHandler(context);
                }
        ));

        chain.sparkChainHandler(new DispatchContext(message)).join();
    }

    @Test
    void dispatch_handler_ignores_unrouted_message() {
        AtomicInteger fanoutCount = new AtomicInteger();
        DispatchHandlerChain chain = new DispatchHandlerChain(List.of(
                new FanoutDispatchChainHandler(ignored -> {
                    fanoutCount.incrementAndGet();
                    return CompletableFuture.completedFuture(null);
                })
        ));

        chain.sparkChainHandler(new DispatchContext(message("/queue/no-route"))).join();

        assertThat(fanoutCount).hasValue(0);
    }

    @Test
    void dispatch_handler_uses_routed_destination() {
        AtomicInteger fanoutCount = new AtomicInteger();
        Message message = message("/queue/fanout");
        DispatchContext context = new DispatchContext(message);
        context.setRoutedMessage(message);
        DispatchHandlerChain chain = new DispatchHandlerChain(List.of(
                new FanoutDispatchChainHandler(destination -> {
                    assertThat(destination).isEqualTo(message.getDestination());
                    fanoutCount.incrementAndGet();
                    return CompletableFuture.completedFuture(null);
                })
        ));

        chain.sparkChainHandler(context).join();

        assertThat(fanoutCount).hasValue(1);
    }

    @Test
    void chain_runs_handlers_on_supplied_executor() {
        try (var executor = Executors.newSingleThreadExecutor()) {
            Message message = message("/queue/async-route");
            Thread callerThread = Thread.currentThread();
            AtomicReference<Thread> handlerThread = new AtomicReference<>();
            DispatchHandlerChain chain = new DispatchHandlerChain(executor, List.of(
                    new RouteDispatchChainHandler(ignored -> {
                        handlerThread.set(Thread.currentThread());
                        return message;
                    })
            ));

            CompletableFuture<?> future = chain.sparkChainHandler(new DispatchContext(message));

            future.join();
            assertThat(handlerThread.get()).isNotSameAs(callerThread);
        }
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
