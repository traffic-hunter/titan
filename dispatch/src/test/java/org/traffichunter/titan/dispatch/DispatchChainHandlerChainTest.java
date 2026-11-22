package org.traffichunter.titan.dispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.traffichunter.titan.core.message.DispatchRejectException;
import org.traffichunter.titan.core.message.DispatchResult;
import org.traffichunter.titan.core.message.Message;
import org.traffichunter.titan.bootstrap.GlobalShutdownHook;
import org.traffichunter.titan.bootstrap.ServerSettings;
import org.traffichunter.titan.bootstrap.TitanShutdownHook;
import org.traffichunter.titan.core.spi.ManagedServer;
import org.traffichunter.titan.core.spi.StompManagedServer;
import org.traffichunter.titan.core.channel.EventLoopGroups;
import org.traffichunter.titan.core.transport.stomp.StompServer;
import org.traffichunter.titan.core.transport.stomp.option.StompServerOption;
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
        TriePathFinder pathFinder = new TriePathFinder();
        DispatchHandlerChain chain = new DispatchHandlerChain(List.of(
                new RouteDispatchChainHandler(pathFinder),
                (context, chainContext) -> {
                    DispatcherQueue queue = pathFinder.get(message.getDestination());
                    assertThat(queue).isNotNull();
                    assertThat(queue.contains(message)).isTrue();
                    assertThat(context.getMessage()).isSameAs(message);
                    return chainContext.next(context);
                }
        ));

        chain.sparkDispatch(new DispatchContext(message)).join();
    }

    @Test
    void fanout_dispatcher_delivers_a_queue_the_path_finder_creates() throws Exception {
        Message message = message("/queue/fanout");
        TriePathFinder pathFinder = new TriePathFinder();
        CountDownLatch exported = new CountDownLatch(1);

        FanoutDispatcher dispatcher = new FanoutDispatcher(exporter(exported, message.getDestination()), pathFinder);

        try {
            pathFinder.getOrPut(message.getDestination()).enqueue(message);
            assertThat(exported.await(1, TimeUnit.SECONDS)).isTrue();
        } finally {
            dispatcher.close();
        }
    }

    @Test
    void fanout_dispatcher_delivers_a_queue_created_before_it_listened() throws Exception {
        Message message = message("/queue/fanout-existing");
        TriePathFinder pathFinder = new TriePathFinder();
        pathFinder.getOrPut(message.getDestination()).enqueue(message);
        CountDownLatch exported = new CountDownLatch(1);

        FanoutDispatcher dispatcher = new FanoutDispatcher(exporter(exported, message.getDestination()), pathFinder);

        try {
            assertThat(exported.await(1, TimeUnit.SECONDS)).isTrue();
        } finally {
            dispatcher.close();
        }
    }

    @ParameterizedTest
    @EnumSource(value = DispatchResult.class, names = "ACCEPTED", mode = EnumSource.Mode.EXCLUDE)
    void route_handler_stops_chain_when_queue_refuses_message(DispatchResult expected) {
        Message message = message("/queue/refused-" + expected.name());
        TriePathFinder pathFinder = new TriePathFinder();
        DispatcherQueue queue = pathFinder.getOrPut(message.getDestination(),
                expected == DispatchResult.TOO_LARGE ? message.getSize() - 1 : message.getSize());
        switch (expected) {
            case FULL -> assertThat(queue.enqueue(message)).isEqualTo(DispatchResult.ACCEPTED);
            case PAUSED -> queue.pause();
            case CLOSED -> queue.close();
            default -> { }
        }
        AtomicInteger laterCalls = new AtomicInteger();
        DispatchHandlerChain chain = DispatchHandlerChain.chain()
                .add(new RouteDispatchChainHandler(pathFinder))
                .add((context, continuation) -> {
                    laterCalls.incrementAndGet();
                    return continuation.next(context);
                });

        CompletableFuture<Void> result = chain.sparkDispatch(new DispatchContext(message));

        assertThatThrownBy(result::join)
                .hasCauseInstanceOf(DispatchRejectException.class)
                .hasRootCauseMessage("Dispatcher queue refused the message. group="
                        + message.getGroup() + ", destination=" + message.getDestination().path() + ", reason=" + expected);
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

    @Test
    void fanout_consumer_takes_a_backlog_in_bounded_blocks_and_keeps_queue_order() throws Exception {
        Destination destination = Destination.create("/queue/blocks");
        TriePathFinder pathFinder = new TriePathFinder();
        int total = 20;
        List<List<String>> blocks = new java.util.concurrent.CopyOnWriteArrayList<>();
        CompletableFuture<@Nullable Void> firstExport = new CompletableFuture<>();
        CountDownLatch firstTaken = new CountDownLatch(1);
        CountDownLatch allExported = new CountDownLatch(total);
        DispatchExporter exporter = new DispatchExporter() {
            @Override
            public String name() {
                return "test";
            }

            @Override
            public CompletionStage<@Nullable Void> export(String group, Destination target, Buffer payload) {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletionStage<@Nullable Void> export(String group, Destination target, List<Message> block) {
                boolean first = blocks.isEmpty();
                blocks.add(block.stream().map(message -> new String(message.getBody())).toList());
                block.forEach(ignored -> allExported.countDown());
                if (first) {
                    firstTaken.countDown();
                    return firstExport;
                }
                return CompletableFuture.completedFuture(null);
            }
        };

        FanoutDispatcher dispatcher = new FanoutDispatcher(exporter, pathFinder, 8, 16_384);
        DispatcherQueue queue = pathFinder.getOrPut(destination);
        try {
            queue.enqueue(numbered(destination, 0));
            assertThat(firstTaken.await(1, TimeUnit.SECONDS)).isTrue();
            // The rest piles up behind the export that is still running.
            for (int i = 1; i < total; i++) {
                queue.enqueue(numbered(destination, i));
            }
            firstExport.complete(null);

            assertThat(allExported.await(2, TimeUnit.SECONDS)).isTrue();
        } finally {
            dispatcher.close();
        }

        assertThat(blocks).allSatisfy(block -> assertThat(block).hasSizeLessThanOrEqualTo(8));
        assertThat(blocks.get(1)).hasSize(8);
        assertThat(blocks.stream().flatMap(List::stream).toList())
                .containsExactlyElementsOf(java.util.stream.IntStream.range(0, total).mapToObj(String::valueOf).toList());
    }

    @Test
    void fanout_consumer_exports_one_message_at_a_time_by_default() throws Exception {
        List<Integer> sizes = backlogBlockSizes(
                (exporter, pathFinder) -> new FanoutDispatcher(exporter, pathFinder));

        assertThat(sizes).hasSize(20).containsOnly(1);
    }

    @Test
    void fanout_consumer_ends_a_block_once_it_reaches_the_byte_limit() throws Exception {
        // Bodies are one or two bytes, so three bytes are reached after two or three messages.
        List<Integer> sizes = backlogBlockSizes(
                (exporter, pathFinder) -> new FanoutDispatcher(exporter, pathFinder, 8, 3));

        assertThat(sizes.subList(1, sizes.size())).allSatisfy(size -> assertThat(size).isBetween(1, 3));
        assertThat(sizes.get(1)).isEqualTo(3);
    }

    @Test
    void fanout_dispatcher_runs_its_handlers_on_each_message_before_the_exporter() throws Exception {
        Destination destination = Destination.create("/queue/handler-order");
        TriePathFinder pathFinder = new TriePathFinder();
        List<String> calls = new java.util.concurrent.CopyOnWriteArrayList<>();
        CountDownLatch exported = new CountDownLatch(2);
        FanoutDispatcher dispatcher = new FanoutDispatcher(
                recordingExporter(block -> calls.add("export:" + block), exported), pathFinder);
        dispatcher.chainHandler(chain -> chain.add((context, next) -> {
            calls.add("handler:" + new String(context.getMessage().getBody()));
            return next.next(context);
        }));

        DispatcherQueue queue = pathFinder.getOrPut(destination);
        try {
            queue.enqueue(numbered(destination, 0));
            queue.enqueue(numbered(destination, 1));
            assertThat(exported.await(1, TimeUnit.SECONDS)).isTrue();
        } finally {
            dispatcher.close();
        }

        assertThat(calls).containsExactly("handler:0", "export:[0]", "handler:1", "export:[1]");
    }

    @Test
    void fanout_dispatcher_drops_a_message_its_handler_stops_and_delivers_the_next() throws Exception {
        Destination destination = Destination.create("/queue/handler-stop");
        TriePathFinder pathFinder = new TriePathFinder();
        List<List<String>> blocks = new java.util.concurrent.CopyOnWriteArrayList<>();
        CountDownLatch exported = new CountDownLatch(1);
        FanoutDispatcher dispatcher = new FanoutDispatcher(recordingExporter(blocks::add, exported), pathFinder);
        dispatcher.chainHandler(chain -> chain.add((context, next) ->
                "0".equals(new String(context.getMessage().getBody())) ? next : next.next(context)));

        // Room for one message, so the second is only admitted once the first is completed.
        DispatcherQueue queue = pathFinder.getOrPut(destination, 1);
        try {
            assertThat(queue.enqueue(numbered(destination, 0))).isEqualTo(DispatchResult.ACCEPTED);
            org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(1)).untilAsserted(() ->
                    assertThat(queue.enqueue(numbered(destination, 1))).isEqualTo(DispatchResult.ACCEPTED));
            assertThat(exported.await(1, TimeUnit.SECONDS)).isTrue();
        } finally {
            dispatcher.close();
        }

        assertThat(blocks).containsExactly(List.of("1"));
    }

    @Test
    void fanout_dispatcher_exports_the_rest_of_a_block_when_its_handler_stops_one_message() throws Exception {
        Destination destination = Destination.create("/queue/handler-block");
        TriePathFinder pathFinder = new TriePathFinder();
        List<List<String>> blocks = new java.util.concurrent.CopyOnWriteArrayList<>();
        CompletableFuture<@Nullable Void> firstExport = new CompletableFuture<>();
        CountDownLatch firstTaken = new CountDownLatch(1);
        CountDownLatch exported = new CountDownLatch(2);
        DispatchExporter exporter = new DispatchExporter() {
            @Override
            public String name() {
                return "test";
            }

            @Override
            public CompletionStage<@Nullable Void> export(String group, Destination target, Buffer payload) {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletionStage<@Nullable Void> export(String group, Destination target, List<Message> block) {
                boolean first = blocks.isEmpty();
                blocks.add(block.stream().map(message -> new String(message.getBody())).toList());
                exported.countDown();
                if (first) {
                    firstTaken.countDown();
                    return firstExport;
                }
                return CompletableFuture.completedFuture(null);
            }
        };
        FanoutDispatcher dispatcher = new FanoutDispatcher(exporter, pathFinder, 8, 16_384);
        dispatcher.chainHandler(chain -> chain.add((context, next) ->
                "2".equals(new String(context.getMessage().getBody())) ? next : next.next(context)));

        DispatcherQueue queue = pathFinder.getOrPut(destination);
        try {
            queue.enqueue(numbered(destination, 0));
            assertThat(firstTaken.await(1, TimeUnit.SECONDS)).isTrue();
            // The next three pile up behind the export that is still running and leave as one block.
            for (int i = 1; i <= 3; i++) {
                queue.enqueue(numbered(destination, i));
            }
            firstExport.complete(null);

            assertThat(exported.await(1, TimeUnit.SECONDS)).isTrue();
        } finally {
            dispatcher.close();
        }

        assertThat(blocks).containsExactly(List.of("0"), List.of("1", "3"));
    }

    @Test
    void fanout_dispatcher_keeps_delivering_after_its_handler_throws() throws Exception {
        Destination destination = Destination.create("/queue/handler-throws");
        TriePathFinder pathFinder = new TriePathFinder();
        List<List<String>> blocks = new java.util.concurrent.CopyOnWriteArrayList<>();
        CountDownLatch exported = new CountDownLatch(1);
        FanoutDispatcher dispatcher = new FanoutDispatcher(recordingExporter(blocks::add, exported), pathFinder);
        dispatcher.chainHandler(chain -> chain.add((context, next) -> {
            if ("0".equals(new String(context.getMessage().getBody()))) {
                throw new IllegalStateException("handler failed");
            }
            return next.next(context);
        }));

        DispatcherQueue queue = pathFinder.getOrPut(destination);
        try {
            queue.enqueue(numbered(destination, 0));
            queue.enqueue(numbered(destination, 1));
            assertThat(exported.await(1, TimeUnit.SECONDS)).isTrue();
        } finally {
            dispatcher.close();
        }

        assertThat(blocks).containsExactly(List.of("1"));
    }

    @ParameterizedTest
    @CsvSource({
            "max-message-block-size, 0",
            "max-message-block-size, 65",
            "max-message-block-size, many",
            "max-message-block-bytes, 0",
            "max-message-block-bytes, 32769"
    })
    void fanout_launcher_rejects_a_message_block_option_out_of_range(String option, String value) {
        StompServerFanoutLauncher launcher = new StompServerFanoutLauncher();

        assertThatThrownBy(() -> launcher.apply("stomp", "tcp", Map.of(option, value), mock(ManagedServer.class)))
                .hasMessageContaining(option);
    }

    @Test
    void fanout_launcher_registers_a_queue_manager_under_the_server_name() {
        // The server is never started, so there is nothing to shut down afterwards.
        StompServer server = StompServer.open(EventLoopGroups.singleGroup(), StompServerOption.builder().build());
        ServerSettings settings = new ServerSettings(
                "launcher-test", "tcp", "stomp", "localhost", 61613, 1, 1, Map.of(), Map.of(), Map.of(), null);

        try {
            new StompServerFanoutLauncher()
                    .apply("stomp", "tcp", Map.of(), new StompManagedServer(server, settings));

            DispatcherQueueManager manager = DispatcherQueueManagers.get("launcher-test");
            assertThat(manager).isNotNull();
            Destination destination = Destination.create("/queue/launcher-managed");
            DispatcherQueue queue = manager.createQueue("default", destination, 1024);
            assertThat(queue.route()).isEqualTo(destination);
            assertThat(manager.deleteQueue("default", destination, true).isDeleted()).isTrue();
        } finally {
            DispatcherQueueManagers.unregister("launcher-test");
        }
    }

    @Test
    void fanout_launcher_closes_the_dispatcher_one_shutdown_step_after_the_gateway() throws Exception {
        // Stays enabled for this JVM. The hook is never registered with the runtime, so its
        // callbacks only run when this test runs them.
        GlobalShutdownHook.INSTANCE.enableShutdownHook();
        StompServer server = StompServer.open(EventLoopGroups.singleGroup(), StompServerOption.builder().build());
        ServerSettings settings = new ServerSettings(
                "shutdown-order-test", "tcp", "stomp", "localhost", 61613, 1, 1, Map.of(), Map.of(), Map.of(), null);
        Destination afterGateway = Destination.create("/queue/shutdown-order/after-gateway");
        Destination afterDispatcher = Destination.create("/queue/shutdown-order/after-dispatcher");
        List<Boolean> dispatcherOpen = new ArrayList<>();

        new StompServerFanoutLauncher()
                .apply("stomp", "tcp", Map.of(), new StompManagedServer(server, settings));
        DispatcherQueueManager manager = DispatcherQueueManagers.get("shutdown-order-test");
        assertThat(manager).isNotNull();

        try {
            // Same step as the gateway's close and added after it, so this runs right behind it.
            GlobalShutdownHook.INSTANCE.addShutdownCallback(GlobalShutdownHook.DISPATCH_ORDER,
                    () -> dispatcherOpen.add(startsDelivery(manager, afterGateway)));
            GlobalShutdownHook.INSTANCE.addShutdownCallback(GlobalShutdownHook.DISPATCH_ORDER + 1,
                    () -> dispatcherOpen.add(startsDelivery(manager, afterDispatcher)));

            runShutdownCallbacks();

            assertThat(dispatcherOpen).containsExactly(true, false);
        } finally {
            manager.deleteQueue("default", afterGateway, true);
            manager.deleteQueue("default", afterDispatcher, true);
            DispatcherQueueManagers.unregister("shutdown-order-test");
        }
    }

    /** A new queue starts its consumer on creation, which a closed dispatcher refuses. */
    private static boolean startsDelivery(DispatcherQueueManager manager, Destination destination) {
        try {
            manager.createQueue("default", destination, 1024);
            return true;
        } catch (IllegalStateException closed) {
            return false;
        }
    }

    /** The global hook only runs at JVM exit, so the test reaches in to run its callbacks. */
    private static void runShutdownCallbacks() throws Exception {
        java.lang.reflect.Field hook = GlobalShutdownHook.class.getDeclaredField("shutdownHook");
        hook.setAccessible(true);
        ((TitanShutdownHook) hook.get(GlobalShutdownHook.INSTANCE)).run();
    }

    private interface DispatcherFactory {
        FanoutDispatcher create(DispatchExporter exporter, PathFinder pathFinder);
    }

    /** Sizes of the blocks a consumer exports when 19 messages pile up behind a first export. */
    private static List<Integer> backlogBlockSizes(DispatcherFactory factory) throws Exception {
        Destination destination = Destination.create("/queue/block-sizes");
        TriePathFinder pathFinder = new TriePathFinder();
        int total = 20;
        List<Integer> sizes = new java.util.concurrent.CopyOnWriteArrayList<>();
        CompletableFuture<@Nullable Void> firstExport = new CompletableFuture<>();
        CountDownLatch firstTaken = new CountDownLatch(1);
        CountDownLatch allExported = new CountDownLatch(total);
        DispatchExporter exporter = new DispatchExporter() {
            @Override
            public String name() {
                return "test";
            }

            @Override
            public CompletionStage<@Nullable Void> export(String group, Destination target, Buffer payload) {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletionStage<@Nullable Void> export(String group, Destination target, List<Message> block) {
                boolean first = sizes.isEmpty();
                sizes.add(block.size());
                block.forEach(ignored -> allExported.countDown());
                if (first) {
                    firstTaken.countDown();
                    return firstExport;
                }
                return CompletableFuture.completedFuture(null);
            }
        };

        FanoutDispatcher dispatcher = factory.create(exporter, pathFinder);
        DispatcherQueue queue = pathFinder.getOrPut(destination);
        try {
            queue.enqueue(numbered(destination, 0));
            assertThat(firstTaken.await(1, TimeUnit.SECONDS)).isTrue();
            for (int i = 1; i < total; i++) {
                queue.enqueue(numbered(destination, i));
            }
            firstExport.complete(null);

            assertThat(allExported.await(2, TimeUnit.SECONDS)).isTrue();
        } finally {
            dispatcher.close();
        }
        return sizes;
    }

    private static Message numbered(Destination destination, int number) {
        return Message.builder()
                .destination(destination)
                .createdAt(Instant.now())
                .producerId("test")
                .body(String.valueOf(number).getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .build();
    }

    /** Reports the bodies of every exported block and counts the block down. */
    private static DispatchExporter recordingExporter(
            java.util.function.Consumer<List<String>> blocks, CountDownLatch exported) {
        return new DispatchExporter() {
            @Override
            public String name() {
                return "test";
            }

            @Override
            public CompletionStage<@Nullable Void> export(String group, Destination target, Buffer payload) {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletionStage<@Nullable Void> export(String group, Destination target, List<Message> block) {
                blocks.accept(block.stream().map(message -> new String(message.getBody())).toList());
                exported.countDown();
                return CompletableFuture.completedFuture(null);
            }
        };
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
