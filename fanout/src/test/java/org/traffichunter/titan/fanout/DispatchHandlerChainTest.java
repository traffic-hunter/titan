package org.traffichunter.titan.fanout;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
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
                .add((context, next) -> {
                    calls.add("first");
                    return next.sparkChainHandler(context);
                })
                .add((context, next) -> {
                    calls.add("second");
                    return next.sparkChainHandler(context);
                });

        chain.sparkChainHandler(new DispatchContext(message("/queue/publish-order"))).join();

        assertThat(calls).containsExactly("first", "second");
    }

    @Test
    void add_first_runs_handler_before_existing_handlers() {
        List<String> calls = new ArrayList<>();
        DispatchHandlerChain chain = DispatchHandlerChain.chain()
                .addLast((context, next) -> {
                    calls.add("second");
                    return next.sparkChainHandler(context);
                })
                .addFirst((context, next) -> {
                    calls.add("first");
                    return next.sparkChainHandler(context);
                });

        chain.sparkChainHandler(new DispatchContext(message("/queue/publish-add-first"))).join();

        assertThat(calls).containsExactly("first", "second");
    }

    @Test
    void add_all_copies_publish_handlers() {
        List<String> calls = new ArrayList<>();
        DispatchHandlerChain middle = DispatchHandlerChain.chain()
                .add((context, next) -> {
                    calls.add("middle");
                    return next.sparkChainHandler(context);
                });

        DispatchHandlerChain.chain()
                .add((context, next) -> {
                    calls.add("first");
                    return next.sparkChainHandler(context);
                })
                .addAll(middle)
                .add((context, next) -> {
                    calls.add("last");
                    return next.sparkChainHandler(context);
                })
                .sparkChainHandler(new DispatchContext(message("/queue/publish-add-all")))
                .join();

        assertThat(calls).containsExactly("first", "middle", "last");
    }

    @Test
    void chain_runs_handlers_on_supplied_executor() {
        try (var executor = Executors.newSingleThreadExecutor()) {
            Thread callerThread = Thread.currentThread();
            AtomicReference<Thread> handlerThread = new AtomicReference<>();
            DispatchHandlerChain chain = DispatchHandlerChain.chain(executor)
                    .add((context, next) -> {
                        handlerThread.set(Thread.currentThread());
                        return next.sparkChainHandler(context);
                    });

            CompletableFuture<Void> future = chain.sparkChainHandler(new DispatchContext(message("/queue/publish-executor")));

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
