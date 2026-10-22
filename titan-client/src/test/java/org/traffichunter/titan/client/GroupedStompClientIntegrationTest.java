package org.traffichunter.titan.client;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.channel.EventLoopGroups;
import org.traffichunter.titan.core.codec.stomp.StompFrames;
import org.traffichunter.titan.core.codec.stomp.StompFrame;
import org.traffichunter.titan.core.codec.stomp.StompCommand;
import org.traffichunter.titan.core.codec.stomp.StompException;
import org.traffichunter.titan.core.codec.stomp.StompHeaders;
import org.traffichunter.titan.core.codec.stomp.StompHeaders.Elements;
import org.traffichunter.titan.core.channel.stomp.StompNetChannelException;
import org.traffichunter.titan.core.channel.stomp.StompServerCommandHandler;
import org.traffichunter.titan.core.message.DispatchRejectException;
import org.traffichunter.titan.core.message.DispatchResult;
import org.traffichunter.titan.core.message.Message;
import org.traffichunter.titan.core.transport.stomp.StompServer;
import org.traffichunter.titan.core.transport.stomp.option.StompServerOption;
import org.traffichunter.titan.core.util.Destination;
import org.traffichunter.titan.core.util.DestinationGroups;
import org.traffichunter.titan.core.util.buffer.Buffer;
import org.traffichunter.titan.dispatch.DestinationGroupRegistry;
import org.traffichunter.titan.dispatch.DispatchGateway;
import org.traffichunter.titan.dispatch.DispatcherQueue;
import org.traffichunter.titan.dispatch.StompSendToFanoutHandler;
import org.traffichunter.titan.dispatch.exporter.StompDispatchExporter;

/**
 * Drives the group API of {@link TitanClient} against a server whose SEND frames go through the
 * dispatcher, so the queues the messages travel through are the real ones.
 */
class GroupedStompClientIntegrationTest {

    private static final String DESTINATION = "/queue/price";

    private final List<DefaultTitanClient> clients = new ArrayList<>();
    private DestinationGroupRegistry registry;
    private DispatchGateway gateway;
    private StompServer server;

    @AfterEach
    void tearDown() throws Exception {
        for (DefaultTitanClient client : clients) {
            client.shutdown(10, SECONDS);
        }
        if (gateway != null) {
            gateway.close();
        }
        if (server != null && !server.isShutdown()) {
            server.shutdown(5, SECONDS);
        }
    }

    @Test
    @Timeout(value = 30, unit = SECONDS)
    void a_message_reaches_only_the_subscribers_of_its_group() throws Exception {
        int port = startDispatchingServer("");
        DefaultTitanClient client = connect(port, "");

        BlockingQueue<StompFrames> market = new LinkedBlockingQueue<>();
        BlockingQueue<StompFrames> notification = new LinkedBlockingQueue<>();
        BlockingQueue<StompFrames> plain = new LinkedBlockingQueue<>();
        client.subscribe("market", DESTINATION, market::add).get(5, SECONDS);
        client.subscribe("notification", DESTINATION, notification::add).get(5, SECONDS);
        client.subscribe(DESTINATION, plain::add).get(5, SECONDS);

        client.send("market", DESTINATION, "m1").get(5, SECONDS);
        client.send("notification", DESTINATION, "n1").get(5, SECONDS);
        client.send(DESTINATION, Buffer.heap().alloc("d1")).get(5, SECONDS);

        assertThat(body(market.poll(10, SECONDS))).isEqualTo("m1");
        assertThat(body(notification.poll(10, SECONDS))).isEqualTo("n1");
        assertThat(body(plain.poll(10, SECONDS))).isEqualTo("d1");

        assertThat(market.poll(500, TimeUnit.MILLISECONDS)).isNull();
        assertThat(notification.poll(500, TimeUnit.MILLISECONDS)).isNull();
        assertThat(plain.poll(500, TimeUnit.MILLISECONDS)).isNull();

        Destination destination = Destination.create(DESTINATION);
        assertThat(registry.get("market", destination))
                .isNotNull()
                .isNotSameAs(registry.get(destination));
        assertThat(registry.get("notification", destination)).isNotNull();
    }

    @Test
    @Timeout(value = 30, unit = SECONDS)
    void a_grouped_message_carries_the_group_back_and_a_plain_one_does_not() throws Exception {
        int port = startDispatchingServer("");
        DefaultTitanClient client = connect(port, "");

        BlockingQueue<StompFrames> market = new LinkedBlockingQueue<>();
        BlockingQueue<StompFrames> plain = new LinkedBlockingQueue<>();
        client.subscribe("market", DESTINATION, market::add).get(5, SECONDS);
        client.subscribe(DESTINATION, plain::add).get(5, SECONDS);

        client.send("market", DESTINATION, "m1").get(5, SECONDS);
        client.send(DESTINATION, Buffer.heap().alloc("d1")).get(5, SECONDS);

        StompFrames grouped = market.poll(10, SECONDS);
        StompFrames ungrouped = plain.poll(10, SECONDS);
        assertThat(grouped).isNotNull();
        assertThat(grouped.getHeader(Elements.GROUP)).isEqualTo("market");
        assertThat(ungrouped).isNotNull();
        assertThat(ungrouped.getHeader(Elements.GROUP)).isNull();
    }

    @Test
    @Timeout(value = 30, unit = SECONDS)
    void one_group_and_destination_can_be_subscribed_to_twice() throws Exception {
        int port = startDispatchingServer("");
        DefaultTitanClient client = connect(port, "");

        BlockingQueue<StompFrames> first = new LinkedBlockingQueue<>();
        BlockingQueue<StompFrames> second = new LinkedBlockingQueue<>();
        String firstId = client.subscribe("market", DESTINATION, first::add).get(5, SECONDS);
        String secondId = client.subscribe("market", DESTINATION, second::add).get(5, SECONDS);

        assertThat(firstId).isNotEqualTo(secondId);

        client.send("market", DESTINATION, "m1").get(5, SECONDS);

        assertThat(body(first.poll(10, SECONDS))).isEqualTo("m1");
        assertThat(body(second.poll(10, SECONDS))).isEqualTo("m1");
    }

    @Test
    @Timeout(value = 30, unit = SECONDS)
    void the_header_api_gets_its_own_identifiers_too() throws Exception {
        int port = startDispatchingServer("");
        DefaultTitanClient client = connect(port, "");

        BlockingQueue<StompFrames> market = new LinkedBlockingQueue<>();
        BlockingQueue<StompFrames> plain = new LinkedBlockingQueue<>();
        // Neither call names an id. Falling back to the destination would give both the same one
        // and the second subscription would take the first one's place.
        String marketId = client
                .subscribe(DESTINATION, Map.of(Elements.GROUP, "market"), market::add)
                .get(5, SECONDS);
        String plainId = client.subscribe(DESTINATION, plain::add).get(5, SECONDS);

        assertThat(marketId).isNotEqualTo(plainId);
        assertThat(marketId).isNotEqualTo(DESTINATION);
        assertThat(plainId).isNotEqualTo(DESTINATION);

        client.send("market", DESTINATION, "m1").get(5, SECONDS);
        client.send(DESTINATION, Buffer.heap().alloc("d1")).get(5, SECONDS);

        assertThat(body(market.poll(10, SECONDS))).isEqualTo("m1");
        assertThat(body(plain.poll(10, SECONDS))).isEqualTo("d1");
        assertThat(market.poll(500, TimeUnit.MILLISECONDS)).isNull();
        assertThat(plain.poll(500, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    @Timeout(value = 30, unit = SECONDS)
    void unsubscribing_one_group_leaves_the_other_receiving() throws Exception {
        int port = startDispatchingServer("");
        DefaultTitanClient client = connect(port, "");

        BlockingQueue<StompFrames> market = new LinkedBlockingQueue<>();
        BlockingQueue<StompFrames> notification = new LinkedBlockingQueue<>();
        String marketId = client.subscribe("market", DESTINATION, market::add).get(5, SECONDS);
        client.subscribe("notification", DESTINATION, notification::add).get(5, SECONDS);

        client.unsubscribe(marketId).get(5, SECONDS);

        client.send("market", DESTINATION, "m1").get(5, SECONDS);
        client.send("notification", DESTINATION, "n1").get(5, SECONDS);

        assertThat(body(notification.poll(10, SECONDS))).isEqualTo("n1");
        assertThat(market.poll(500, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    @Timeout(value = 30, unit = SECONDS)
    void groups_are_isolated_over_websocket_too() throws Exception {
        int port = startDispatchingServer("/stomp");
        DefaultTitanClient client = connect(port, "/stomp");

        BlockingQueue<StompFrames> market = new LinkedBlockingQueue<>();
        BlockingQueue<StompFrames> plain = new LinkedBlockingQueue<>();
        client.subscribe("market", DESTINATION, market::add).get(5, SECONDS);
        client.subscribe(DESTINATION, plain::add).get(5, SECONDS);

        client.send("market", DESTINATION, "m1").get(5, SECONDS);

        assertThat(body(market.poll(10, SECONDS))).isEqualTo("m1");
        assertThat(plain.poll(500, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    @Timeout(value = 30, unit = SECONDS)
    void a_group_the_server_refuses_reports_an_error_frame() throws Exception {
        int port = startDispatchingServer("");
        DefaultTitanClient client = connect(port, "");

        BlockingQueue<StompFrames> errors = new LinkedBlockingQueue<>();
        client.errorHandler(errors::add);

        // The client refuses a malformed name itself, so the header has to be planted by hand
        // to see what the server does with one.
        client.send(DESTINATION, Buffer.heap().alloc("x"), Map.of(Elements.GROUP, "bad/name"));

        StompFrames error = errors.poll(10, SECONDS);
        assertThat(error).isNotNull();
        assertThat(error.getHeader(Elements.MESSAGE)).contains("Wrong send.");
    }

    @Test
    @Timeout(value = 30, unit = SECONDS)
    void editing_the_header_map_after_subscribing_changes_nothing() throws Exception {
        int port = startDispatchingServer("");
        DefaultTitanClient client = connect(port, "");

        BlockingQueue<StompFrames> market = new LinkedBlockingQueue<>();
        Map<Elements, String> headers = new HashMap<>();
        String id = client.subscribe("market", DESTINATION, headers, market::add).get(5, SECONDS);
        headers.put(Elements.GROUP, "notification");
        headers.put(Elements.ID, "hijacked");

        client.send("market", DESTINATION, "m1").get(5, SECONDS);

        StompFrames delivered = market.poll(10, SECONDS);
        assertThat(delivered).isNotNull();
        assertThat(delivered.getHeader(Elements.SUBSCRIPTION)).isEqualTo(id);
        assertThat(delivered.getHeader(Elements.GROUP)).isEqualTo("market");
    }

    @ParameterizedTest
    @EnumSource(value = DispatchResult.class, names = "ACCEPTED", mode = EnumSource.Mode.EXCLUDE)
    @Timeout(value = 30, unit = SECONDS)
    void queue_refusal_fails_the_send_future_and_preserves_error_callback(DispatchResult expected) throws Exception {
        int port = startDispatchingServer("");
        Destination destination = Destination.create(DESTINATION);
        DispatcherQueue queue = gateway.createQueue("market", destination,
                expected == DispatchResult.TOO_LARGE ? 1 : 5);
        switch (expected) {
            case FULL -> assertThat(queue.enqueue(Message.builder()
                    .group("market")
                    .destination(destination)
                    .createdAt(Instant.now())
                    .producerId("test")
                    .body(new byte[5])
                    .build())).isEqualTo(DispatchResult.ACCEPTED);
            case PAUSED -> queue.pause();
            case CLOSED -> queue.close();
            default -> { }
        }
        DefaultTitanClient client = connect(port, "");
        BlockingQueue<StompFrames> errors = new LinkedBlockingQueue<>();
        client.errorHandler(errors::add);

        CompletableFuture<StompFrames> sent = client.send(DESTINATION, Buffer.heap().alloc("hello"),
                Map.of(Elements.GROUP, "market", Elements.RECEIPT, "send:" + expected.code()));

        ExecutionException failure = assertThrows(ExecutionException.class, () -> sent.get(5, SECONDS));
        assertThat(failure.getCause()).isInstanceOfSatisfying(DispatchRejectException.class, rejection -> {
            assertThat(rejection.result()).isEqualTo(expected);
            assertThat(rejection.getMessage()).contains("group=market", "destination=" + DESTINATION);
        });
        StompFrames error = errors.poll(5, SECONDS);
        assertThat(error).isNotNull();
        assertThat(error.getHeader(Elements.MESSAGE)).isEqualTo(Integer.toString(expected.code()));
        assertThat(error.getHeader(Elements.RECEIPT_ID)).isEqualTo("send:" + expected.code());
    }

    @Test
    @Timeout(value = 30, unit = SECONDS)
    void websocket_send_future_preserves_queue_refusal() throws Exception {
        int port = startDispatchingServer("/stomp");
        DispatcherQueue queue = gateway.createQueue(DestinationGroups.DEFAULT, Destination.create(DESTINATION), 1024);
        queue.pause();
        DefaultTitanClient client = connect(port, "/stomp");

        CompletableFuture<StompFrames> sent = client.send(DESTINATION, Buffer.heap().alloc("hello"),
                Map.of(Elements.RECEIPT, "ws-send"));

        ExecutionException failure = assertThrows(ExecutionException.class, () -> sent.get(5, SECONDS));
        assertThat(failure.getCause()).isInstanceOfSatisfying(DispatchRejectException.class,
                rejection -> assertThat(rejection.result()).isEqualTo(DispatchResult.PAUSED));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"0", "9999", "Wrong send.", "2147483648"})
    @Timeout(value = 30, unit = SECONDS)
    void unrecognized_error_code_fails_request_without_claiming_admission_refusal(@Nullable String code)
            throws Exception {
        int port = startDispatchingServer("", (event, context) -> {
            StompFrame response = StompFrame.create(StompHeaders.create(), StompCommand.ERROR,
                    "Server rejected the request".getBytes(StandardCharsets.UTF_8));
            if (code != null) {
                response.addHeader(Elements.MESSAGE, code);
            }
            context.sendErrorAndClose(event.frame(), response, event.connection());
        });
        DefaultTitanClient client = connect(port, "");

        CompletableFuture<StompFrames> sent = client.send(DESTINATION, Buffer.heap().alloc("hello"),
                Map.of(Elements.RECEIPT, "send-unknown"));

        ExecutionException failure = assertThrows(ExecutionException.class, () -> sent.get(5, SECONDS));
        assertThat(failure.getCause()).isInstanceOf(StompException.class)
                .isNotInstanceOf(DispatchRejectException.class)
                .hasMessage("Server rejected the request");
    }

    @Test
    @Timeout(value = 30, unit = SECONDS)
    void error_receipt_identifies_only_the_rejected_request() throws Exception {
        int port = startDispatchingServer("", (event, context) -> {
            if ("second".equals(event.frame().getHeader(Elements.RECEIPT))) {
                context.sendErrorAndClose(event.frame(), StompFrame.errorFrame("1000", "Queue is full"),
                        event.connection());
            }
        });
        DefaultTitanClient client = connect(port, "");

        CompletableFuture<StompFrames> first = client.send(DESTINATION, Buffer.heap().alloc("first"),
                Map.of(Elements.RECEIPT, "first"));
        CompletableFuture<StompFrames> second = client.send(DESTINATION, Buffer.heap().alloc("second"),
                Map.of(Elements.RECEIPT, "second"));

        ExecutionException rejected = assertThrows(ExecutionException.class, () -> second.get(5, SECONDS));
        assertThat(rejected.getCause()).isInstanceOfSatisfying(DispatchRejectException.class,
                error -> assertThat(error.result()).isEqualTo(DispatchResult.FULL));
        ExecutionException unconfirmed = assertThrows(ExecutionException.class, () -> first.get(5, SECONDS));
        assertThat(unconfirmed.getCause()).isInstanceOf(StompNetChannelException.class)
                .isNotInstanceOf(DispatchRejectException.class);
    }

    @Test
    @Timeout(value = 30, unit = SECONDS)
    void send_without_receipt_reports_refusal_through_error_callback() throws Exception {
        int port = startDispatchingServer("");
        gateway.createQueue(DestinationGroups.DEFAULT, Destination.create(DESTINATION), 1024).pause();
        DefaultTitanClient client = connect(port, "");
        BlockingQueue<StompFrames> errors = new LinkedBlockingQueue<>();
        client.errorHandler(errors::add);

        client.send(DESTINATION, Buffer.heap().alloc("hello")).get(5, SECONDS);

        StompFrames error = errors.poll(5, SECONDS);
        assertThat(error).isNotNull();
        assertThat(error.getHeader(Elements.MESSAGE)).isEqualTo("1001");
        assertThat(error.getHeader(Elements.RECEIPT_ID)).isNull();
    }

    @Test
    @Timeout(value = 30, unit = SECONDS)
    void failure_after_enqueue_is_not_exposed_as_a_queue_refusal() throws Exception {
        int port = startDispatchingServer("");
        gateway.chainHandler(chain -> chain.add((context, continuation) -> {
            throw new IllegalStateException("Handler failed after enqueue");
        }));
        DefaultTitanClient client = connect(port, "");

        CompletableFuture<StompFrames> sent = client.send(DESTINATION, Buffer.heap().alloc("hello"),
                Map.of(Elements.RECEIPT, "send-unconfirmed"));

        ExecutionException failure = assertThrows(ExecutionException.class, () -> sent.get(5, SECONDS));
        assertThat(failure.getCause()).isInstanceOf(StompException.class)
                .isNotInstanceOf(DispatchRejectException.class);
        DispatcherQueue queue = registry.get(Destination.create(DESTINATION));
        assertThat(queue).isNotNull();
        assertThat(queue.size()).isOne();
    }

    private int startDispatchingServer(String webSocketPath) throws Exception {
        return startDispatchingServer(webSocketPath, null);
    }

    private int startDispatchingServer(String webSocketPath, @Nullable StompServerCommandHandler sendHandler)
            throws Exception {
        registry = new DestinationGroupRegistry();
        StompServer opened = StompServer.open(
                EventLoopGroups.group(1, 1),
                StompServerOption.builder().build()
        );
        server = webSocketPath.isEmpty() ? opened : opened.webSocket(webSocketPath);
        gateway = DispatchGateway.of(new StompDispatchExporter(server.connection()), registry);
        server.onStomp(handler -> handler.sendHandler(
                sendHandler == null ? new StompSendToFanoutHandler(gateway) : sendHandler));
        server.start();
        server.listen("localhost", 0).get(5, SECONDS);
        return ((InetSocketAddress) server.connection().channel().localAddress()).getPort();
    }

    private DefaultTitanClient connect(int port, String webSocketPath) throws Exception {
        ClientConfiguration.Builder configuration = ClientConfiguration.builder()
                .host("localhost")
                .port(port);
        if (!webSocketPath.isEmpty()) {
            configuration.webSocket(webSocketPath);
        }

        DefaultTitanClient client = new DefaultTitanClient(
                new TitanStompClientDriver(EventLoopGroups.singleGroup(), configuration.build())
        );
        clients.add(client);
        client.start();
        client.connect().get(5, SECONDS);
        return client;
    }

    private static String body(StompFrames frame) {
        assertThat(frame).isNotNull();
        return new String(frame.body(), StandardCharsets.UTF_8);
    }
}
