package org.traffichunter.titan.dispatch.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.traffichunter.titan.core.channel.EventLoopGroups;
import org.traffichunter.titan.core.transport.stomp.StompServer;
import org.traffichunter.titan.core.transport.stomp.option.StompServerOption;
import org.traffichunter.titan.core.util.Destination;
import org.traffichunter.titan.dispatch.DestinationGroupRegistry;
import org.traffichunter.titan.dispatch.DispatchGateway;
import org.traffichunter.titan.dispatch.StompSendToFanoutHandler;
import org.traffichunter.titan.dispatch.exporter.StompDispatchExporter;

/**
 * Drives a real STOMP server over raw sockets and checks that the {@code group} header
 * isolates delivery per group.
 *
 * @author yun
 */
class GroupedStompFanoutIntegrationTest {

    private static final String DESTINATION = "/topic/price";

    private final List<RawStompClient> clients = new ArrayList<>();
    private StompServer server;
    private DispatchGateway gateway;
    private DestinationGroupRegistry registry;
    private int port;

    @BeforeEach
    void startServer() throws Exception {
        registry = new DestinationGroupRegistry();
        server = StompServer.open(EventLoopGroups.singleGroup(), StompServerOption.builder().build());
        gateway = DispatchGateway.ofThread(new StompDispatchExporter(server.connection()), registry);
        server.onStomp(handler -> handler.sendHandler(new StompSendToFanoutHandler(gateway)));
        server.start();
        server.listen("localhost", 0).get(5, TimeUnit.SECONDS);
        port = ((InetSocketAddress) server.connection().channel().localAddress()).getPort();
    }

    @AfterEach
    void stopServer() throws Exception {
        for (RawStompClient client : clients) {
            client.close();
        }
        if (gateway != null) {
            gateway.close();
        }
        if (server != null && !server.isShutdown()) {
            server.shutdown(5, TimeUnit.SECONDS);
        }
    }

    @Test
    @Timeout(20)
    void group_header_routes_to_the_matching_subscribers_only() throws Exception {
        RawStompClient market = connect();
        RawStompClient notification = connect();
        RawStompClient plain = connect();
        market.subscribe("market", "sub-market");
        notification.subscribe("notification", "sub-notification");
        plain.subscribe(null, "sub-plain");

        RawStompClient producer = connect();
        producer.send("market", "m1");
        producer.send("notification", "n1");
        producer.send(null, "d1");

        String marketFrame = market.frames.poll(5, TimeUnit.SECONDS);
        String notificationFrame = notification.frames.poll(5, TimeUnit.SECONDS);
        String plainFrame = plain.frames.poll(5, TimeUnit.SECONDS);

        assertThat(marketFrame).startsWith("MESSAGE").contains("group:market", "subscription:sub-market").endsWith("m1");
        assertThat(notificationFrame).startsWith("MESSAGE").contains("group:notification").endsWith("n1");
        assertThat(plainFrame).startsWith("MESSAGE").doesNotContain("group:").endsWith("d1");

        assertThat(market.frames.poll(300, TimeUnit.MILLISECONDS)).isNull();
        assertThat(notification.frames.poll(300, TimeUnit.MILLISECONDS)).isNull();
        assertThat(plain.frames.poll(300, TimeUnit.MILLISECONDS)).isNull();

        Destination destination = Destination.create(DESTINATION);
        assertThat(registry.containsGroup("market")).isTrue();
        assertThat(registry.containsGroup("notification")).isTrue();
        assertThat(registry.get("market", destination)).isNotNull();
        assertThat(registry.get(destination)).isNotNull();
        assertThat(registry.get("market", destination)).isNotSameAs(registry.get(destination));
    }

    @Test
    @Timeout(20)
    void malformed_group_header_is_rejected_with_error_frame() throws Exception {
        RawStompClient producer = connect();

        producer.send("bad/name", "x");

        String error = producer.frames.poll(5, TimeUnit.SECONDS);
        assertThat(error).startsWith("ERROR").contains("Invalid group name: bad/name");
        Awaitility.await().atMost(Duration.ofSeconds(5)).until(producer::isClosedByPeer);
    }

    private RawStompClient connect() throws Exception {
        RawStompClient client = new RawStompClient(port);
        clients.add(client);
        client.write("CONNECT\naccept-version:1.2\nhost:localhost\nheart-beat:0,0\n\n");
        String connected = client.frames.poll(5, TimeUnit.SECONDS);
        assertThat(connected).startsWith("CONNECTED");
        return client;
    }

    /** Minimal STOMP client. Frames arrive on {@link #frames} as text without the trailing NUL. */
    private static final class RawStompClient {

        final BlockingQueue<String> frames = new LinkedBlockingQueue<>();
        private final Socket socket;
        private final OutputStream out;
        private final Thread reader;
        private volatile boolean closedByPeer;
        private int receipts;

        RawStompClient(int port) throws IOException {
            socket = new Socket();
            socket.connect(new InetSocketAddress("localhost", port), 5000);
            out = socket.getOutputStream();
            reader = new Thread(this::readLoop, "raw-stomp-reader");
            reader.setDaemon(true);
            reader.start();
        }

        void subscribe(String group, String id) throws Exception {
            String receipt = "receipt-" + (++receipts);
            StringBuilder frame = new StringBuilder("SUBSCRIBE\n")
                    .append("destination:").append(DESTINATION).append('\n')
                    .append("id:").append(id).append('\n')
                    .append("receipt:").append(receipt).append('\n');
            if (group != null) {
                frame.append("group:").append(group).append('\n');
            }
            write(frame.append('\n').toString());
            // Wait for the RECEIPT so a SEND that follows cannot race the registration.
            String receiptFrame = frames.poll(5, TimeUnit.SECONDS);
            assertThat(receiptFrame).startsWith("RECEIPT").contains("receipt-id:" + receipt);
        }

        void send(String group, String body) throws IOException {
            StringBuilder frame = new StringBuilder("SEND\n")
                    .append("destination:").append(DESTINATION).append('\n');
            if (group != null) {
                frame.append("group:").append(group).append('\n');
            }
            write(frame.append('\n').append(body).toString());
        }

        void write(String frameWithoutNul) throws IOException {
            out.write(frameWithoutNul.getBytes(StandardCharsets.UTF_8));
            out.write(0);
            out.flush();
        }

        boolean isClosedByPeer() {
            return closedByPeer;
        }

        void close() throws IOException {
            socket.close();
        }

        private void readLoop() {
            StringBuilder current = new StringBuilder();
            try (InputStream in = socket.getInputStream()) {
                int b;
                while ((b = in.read()) != -1) {
                    if (b == 0) {
                        String frame = current.toString().strip();
                        current.setLength(0);
                        if (!frame.isEmpty()) {
                            frames.add(frame);
                        }
                        continue;
                    }
                    current.append((char) b);
                }
            } catch (IOException ignored) {
                // socket closed
            } finally {
                closedByPeer = true;
            }
        }
    }
}
