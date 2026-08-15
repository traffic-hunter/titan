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

        chain.sparkDispatch(new DispatchContext(message("/queue/dispatch-order"))).join();

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

        chain.sparkDispatch(new DispatchContext(message("/queue/dispatch-add-first"))).join();

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
                .sparkDispatch(new DispatchContext(message("/queue/dispatch-add-all")))
                .join();

        assertThat(calls).containsExactly("first", "middle", "last");
    }

    @Test
    void chain_runs_handlers_on_supplied_executor() {
        try (var executor = Executors.newSingleThreadExecutor()) {
            Thread callerThread = Thread.currentThread();
            AtomicInteger submissions = new AtomicInteger();
            AtomicReference<Thread> firstHandlerThread = new AtomicReference<>();
            AtomicReference<Thread> secondHandlerThread = new AtomicReference<>();
            DispatchHandlerChain chain = DispatchHandlerChain.chain(command -> {
                        submissions.incrementAndGet();
                        executor.execute(command);
                    })
                    .add((context, chainContext) -> {
                        firstHandlerThread.set(Thread.currentThread());
                        return chainContext.next(context);
                    })
                    .add((context, chainContext) -> {
                        secondHandlerThread.set(Thread.currentThread());
                        return chainContext.next(context);
                    });

            CompletableFuture<Void> future = chain.sparkDispatch(new DispatchContext(message("/queue/dispatch-executor")));

            future.join();
            assertThat(submissions).hasValue(1);
            assertThat(firstHandlerThread.get()).isNotSameAs(callerThread);
            assertThat(secondHandlerThread.get()).isSameAs(firstHandlerThread.get());
        }
    }

    @Test
    void chain_stops_when_handler_fails() {
        RuntimeException failure = new RuntimeException("dispatch failed");
        AtomicInteger laterHandlerCalls = new AtomicInteger();
        DispatchHandlerChain chain = DispatchHandlerChain.chain()
                .add((context, chainContext) -> {
                    throw failure;
                })
                .add((context, chainContext) -> {
                    laterHandlerCalls.incrementAndGet();
                    return chainContext.next(context);
                });

        CompletableFuture<Void> result = chain.sparkDispatch(
                new DispatchContext(message("/queue/dispatch-failure"))
        );

        assertThatThrownBy(result::join).hasCause(failure);
        assertThat(laterHandlerCalls).hasValue(0);
    }

    @Test
    void chain_reports_exception_thrown_by_handler() {
        RuntimeException failure = new RuntimeException("dispatch failed");
        DispatchHandlerChain chain = DispatchHandlerChain.chain()
                .add((context, chainContext) -> {
                    throw failure;
                });

        CompletableFuture<Void> result = chain.sparkDispatch(
                new DispatchContext(message("/queue/dispatch-thrown-failure"))
        );

        assertThatThrownBy(result::join).hasCause(failure);
    }

    @Test
    void handler_can_stop_chain_without_failure() {
        AtomicInteger laterHandlerCalls = new AtomicInteger();
        DispatchHandlerChain chain = DispatchHandlerChain.chain()
                .add((context, chainContext) -> chainContext)
                .add((context, chainContext) -> {
                    laterHandlerCalls.incrementAndGet();
                    return chainContext.next(context);
                });

        chain.sparkDispatch(new DispatchContext(message("/queue/dispatch-short-circuit"))).join();

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
