package org.traffichunter.titan.dispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.jspecify.annotations.Nullable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.traffichunter.titan.core.message.Message;
import org.traffichunter.titan.core.util.Destination;
import org.traffichunter.titan.core.util.buffer.Buffer;
import org.traffichunter.titan.dispatch.exporter.DispatchExporter;

class DispatchChainHandlerChainTest {

    @Test
    void chain_runs_handlers_in_order() {
        List<String> calls = new ArrayList<>();
        DispatchHandlerChain chain = new DispatchHandlerChain()
                .add((context, chainContext) -> {
                    calls.add("first");
                    return chainContext.next(context);
                })
                .add((context, chainContext) -> {
                    calls.add("second");
                    return chainContext.next(context);
                });

        chain.sparkDispatch(new DispatchContext(message("/queue/chain-order"))).join();

        assertThat(calls).containsExactly("first", "second");
    }

    @Test
    void route_handler_routes_message_before_next_handler() {
        Message message = message("/queue/route");
        TrieDispatcher dispatcher = new TrieDispatcher();
        DispatchHandlerChain chain = new DispatchHandlerChain(List.of(
                new RouteDispatchChainHandler(dispatcher),
                (context, chainContext) -> {
                    DispatcherQueue queue = dispatcher.get(message.getDestination());
                    assertThat(queue).isNotNull();
                    assertThat(queue.contains(message)).isTrue();
                    assertThat(context.getMessage()).isSameAs(message);
                    return chainContext.next(context);
                }
        ));

        chain.sparkDispatch(new DispatchContext(message)).join();
    }

    @Test
    void dispatch_handler_fans_out_message_destination() throws Exception {
        Message message = message("/queue/fanout");
        TrieDispatcher dispatcher = new TrieDispatcher();
        dispatcher.getOrPut(message.getDestination()).enqueue(message);
        CountDownLatch exported = new CountDownLatch(1);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            FanoutDispatchChainHandler handler = new FanoutDispatchChainHandler(
                    executor,
                    exporter(exported, message.getDestination()),
                    dispatcher
            );
            DispatchHandlerChain chain = new DispatchHandlerChain(List.of(handler));

            try {
                chain.sparkDispatch(new DispatchContext(message)).join();
                assertThat(exported.await(1, TimeUnit.SECONDS)).isTrue();
            } finally {
                handler.close();
            }
        }
    }

    @ParameterizedTest
    @EnumSource(value = DispatchResult.class, names = "ACCEPTED", mode = EnumSource.Mode.EXCLUDE)
    void route_handler_stops_chain_when_queue_refuses_message(DispatchResult expected) {
        Message message = message("/queue/refused-" + expected.name());
        TrieDispatcher dispatcher = new TrieDispatcher();
        DispatcherQueue queue = dispatcher.getOrPut(message.getDestination(),
                expected == DispatchResult.TOO_LARGE ? message.getSize() - 1 : message.getSize());
        switch (expected) {
            case FULL -> assertThat(queue.enqueue(message)).isEqualTo(DispatchResult.ACCEPTED);
            case PAUSED -> queue.pause();
            case CLOSED -> queue.close();
            default -> { }
        }
        AtomicInteger laterCalls = new AtomicInteger();
        DispatchHandlerChain chain = DispatchHandlerChain.chain()
                .add(new RouteDispatchChainHandler(dispatcher))
                .add((context, continuation) -> {
                    laterCalls.incrementAndGet();
                    return continuation.next(context);
                });

        CompletableFuture<Void> result = chain.sparkDispatch(new DispatchContext(message));

        assertThatThrownBy(result::join)
                .hasCauseInstanceOf(IllegalStateException.class)
                .hasRootCauseMessage("Dispatcher queue refused the message = "
                        + message.getGroup() + ":" + message.getDestination().path() + ", reason=" + expected);
        assertThat(laterCalls).hasValue(0);
        assertThat(queue.size()).isEqualTo(expected == DispatchResult.FULL ? 1 : 0);
        assertThat(queue.getPendingBytes()).isEqualTo(expected == DispatchResult.FULL ? message.getSize() : 0);
    }

    @Test
    void chain_runs_handlers_on_supplied_executor() {
        try (var executor = Executors.newSingleThreadExecutor()) {
            Message message = message("/queue/async-route");
            Thread callerThread = Thread.currentThread();
            AtomicReference<Thread> handlerThread = new AtomicReference<>();
            DispatchHandlerChain chain = new DispatchHandlerChain(executor, List.of(
                    (context, chainContext) -> {
                        handlerThread.set(Thread.currentThread());
                        return chainContext.next(context);
                    }
            ));

            CompletableFuture<?> future = chain.sparkDispatch(new DispatchContext(message));

            future.join();
            assertThat(handlerThread.get()).isNotSameAs(callerThread);
        }
    }

    private static DispatchExporter exporter(CountDownLatch exported, Destination expected) {
        return new DispatchExporter() {
            @Override
            public String name() {
                return "test";
            }

            @Override
            public CompletionStage<@Nullable Void> export(String group, Destination destination, Buffer payload) {
                assertThat(destination).isEqualTo(expected);
                exported.countDown();
                return CompletableFuture.completedFuture(null);
            }
        };
    }

    private static Message message(String destination) {
        return Message.builder()
                .destination(Destination.create(destination))
                .createdAt(Instant.now())
                .producerId("test")
                .body("test".getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .build();
    }
}
