package org.traffichunter.titan.dispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

class DispatchHandlerChainTest {

    @Test
    void chain_runs_handlers_in_order() {
        List<String> calls = new ArrayList<>();
        DispatchHandlerChain chain = DispatchHandlerChain.chain()
                .add((context, chainContext) -> {
                    calls.add("first");
                    return chainContext.next(context);
                })
                .add((context, chainContext) -> {
                    calls.add("second");
                    return chainContext.next(context);
                });

        chain.dispatch(new DispatchContext(message("/queue/publish-order"))).join();

        assertThat(calls).containsExactly("first", "second");
    }

    @Test
    void add_first_runs_handler_before_existing_handlers() {
        List<String> calls = new ArrayList<>();
        DispatchHandlerChain chain = DispatchHandlerChain.chain()
                .addLast((context, chainContext) -> {
                    calls.add("second");
                    return chainContext.next(context);
                })
                .addFirst((context, chainContext) -> {
                    calls.add("first");
                    return chainContext.next(context);
                });

        chain.dispatch(new DispatchContext(message("/queue/publish-add-first"))).join();

        assertThat(calls).containsExactly("first", "second");
    }

    @Test
    void add_all_appends_handlers_in_order() {
        List<String> calls = new ArrayList<>();
        List<DispatchChainHandler> middle = List.of(
                (context, chainContext) -> {
                    calls.add("middle");
                    return chainContext.next(context);
                });

        DispatchHandlerChain.chain()
                .add((context, chainContext) -> {
                    calls.add("first");
                    return chainContext.next(context);
                })
                .addAll(middle)
                .add((context, chainContext) -> {
                    calls.add("last");
                    return chainContext.next(context);
                })
                .dispatch(new DispatchContext(message("/queue/publish-add-all")))
                .join();

        assertThat(calls).containsExactly("first", "middle", "last");
    }

    @Test
    void chain_runs_handlers_on_supplied_executor() {
        try (var executor = Executors.newSingleThreadExecutor()) {
            Thread callerThread = Thread.currentThread();
            CompletableFuture<Void> firstCompletion = new CompletableFuture<>();
            AtomicReference<Thread> firstHandlerThread = new AtomicReference<>();
            AtomicReference<Thread> secondHandlerThread = new AtomicReference<>();
            DispatchHandlerChain chain = DispatchHandlerChain.chain(executor)
                    .add((context, chainContext) -> {
                        firstHandlerThread.set(Thread.currentThread());
                        return firstCompletion.thenCompose(ignored -> chainContext.next(context));
                    })
                    .add((context, chainContext) -> {
                        secondHandlerThread.set(Thread.currentThread());
                        return chainContext.next(context);
                    });

            CompletableFuture<Void> future = chain.dispatch(new DispatchContext(message("/queue/publish-executor")));
            firstCompletion.complete(null);

            future.join();
            assertThat(firstHandlerThread.get()).isNotSameAs(callerThread);
            assertThat(secondHandlerThread.get()).isSameAs(firstHandlerThread.get());
        }
    }

    @Test
    void chain_waits_for_current_handler_before_running_next_handler() {
        List<String> calls = new ArrayList<>();
        CompletableFuture<Void> firstCompletion = new CompletableFuture<>();
        DispatchHandlerChain chain = DispatchHandlerChain.chain()
                .add((context, chainContext) -> {
                    calls.add("first");
                    return firstCompletion.thenCompose(ignored -> chainContext.next(context));
                })
                .add((context, chainContext) -> {
                    calls.add("second");
                    return chainContext.next(context);
                });

        CompletableFuture<Void> result = chain.dispatch(
                new DispatchContext(message("/queue/publish-async-order"))
        );

        assertThat(calls).containsExactly("first");
        assertThat(result).isNotDone();

        firstCompletion.complete(null);

        result.join();
        assertThat(calls).containsExactly("first", "second");
    }

    @Test
    void chain_stops_when_handler_fails() {
        RuntimeException failure = new RuntimeException("dispatch failed");
        AtomicInteger laterHandlerCalls = new AtomicInteger();
        DispatchHandlerChain chain = DispatchHandlerChain.chain()
                .add((context, chainContext) -> CompletableFuture.failedFuture(failure))
                .add((context, chainContext) -> {
                    laterHandlerCalls.incrementAndGet();
                    return chainContext.next(context);
                });

        CompletableFuture<Void> result = chain.dispatch(
                new DispatchContext(message("/queue/publish-failure"))
        );

        assertThatThrownBy(result::join).hasCause(failure);
        assertThat(laterHandlerCalls).hasValue(0);
    }

    @Test
    void handler_can_stop_chain_without_failure() {
        AtomicInteger laterHandlerCalls = new AtomicInteger();
        DispatchHandlerChain chain = DispatchHandlerChain.chain()
                .add((context, chainContext) -> CompletableFuture.completedFuture(null))
                .add((context, chainContext) -> {
                    laterHandlerCalls.incrementAndGet();
                    return chainContext.next(context);
                });

        chain.dispatch(new DispatchContext(message("/queue/publish-short-circuit"))).join();

        assertThat(laterHandlerCalls).hasValue(0);
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
