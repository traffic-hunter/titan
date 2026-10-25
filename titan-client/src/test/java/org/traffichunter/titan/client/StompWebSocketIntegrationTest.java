/*
 * Copyright 2025 traffic-hunter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traffichunter.titan.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.traffichunter.titan.core.channel.EventLoopGroups;
import org.traffichunter.titan.core.channel.stomp.StompClientWebSocketChannel;
import org.traffichunter.titan.core.transport.stomp.StompServer;
import org.traffichunter.titan.core.transport.stomp.option.StompServerOption;
import org.traffichunter.titan.core.transport.websocket.WebSocketHandshakeException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author yun
 */
class StompWebSocketIntegrationTest {

    @Test
    @Timeout(10)
    void connect_stomp_client_over_default_websocket_path() throws Exception {
        StompServer server = StompServer.open(
                EventLoopGroups.group(1, 1),
                StompServerOption.builder().build()
        ).webSocket("");
        TitanStompClientDriver client = new TitanStompClientDriver(
                EventLoopGroups.group(1, 1),
                ClientConfiguration.builder().webSocket("").build()
        );

        try {
            server.start();
            server.listen("localhost", 0).get(5, TimeUnit.SECONDS);
            int port = ((InetSocketAddress) server.connection().channel().localAddress()).getPort();

            client.start();
            client.connect("localhost", port).get(5, TimeUnit.SECONDS);

            assertThat(client.channel()).isInstanceOf(StompClientWebSocketChannel.class);
            assertThat(client.channel().isConnected()).isTrue();
            assertThat(server.connection().connections())
                    .singleElement()
                    .isInstanceOf(StompClientWebSocketChannel.class);
        } finally {
            client.close();
            if (!server.isShutdown()) {
                server.shutdown();
            }
        }
    }

    @Test
    @Timeout(10)
    void reject_client_using_different_websocket_path() throws Exception {
        StompServer server = StompServer.open(
                EventLoopGroups.group(1, 1),
                StompServerOption.builder().build()
        ).webSocket("/stomp");
        TitanStompClientDriver client = new TitanStompClientDriver(
                EventLoopGroups.group(1, 1),
                ClientConfiguration.builder().webSocket("/wrong").build()
        );

        try {
            server.start();
            server.listen("localhost", 0).get(5, TimeUnit.SECONDS);
            int port = ((InetSocketAddress) server.connection().channel().localAddress()).getPort();
            client.start();

            assertThatThrownBy(() -> client.connect("localhost", port).get(5, TimeUnit.SECONDS))
                    .hasRootCauseInstanceOf(WebSocketHandshakeException.class);
            assertThat(server.connection().connections()).isEmpty();
        } finally {
            client.close();
            if (!server.isShutdown()) {
                server.shutdown();
            }
        }
    }

    @Test
    void reject_server_transport_change_after_start() {
        StompServer server = StompServer.open(
                EventLoopGroups.group(1, 1),
                StompServerOption.builder().build()
        );

        try {
            server.start();

            assertThatThrownBy(() -> server.webSocket("/stomp"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("after start");
        } finally {
            server.shutdown();
        }
    }

    @Test
    @Timeout(20)
    void close_connection_when_declared_websocket_payload_exceeds_stomp_frame_limit() throws Exception {
        StompServer server = StompServer.open(
                EventLoopGroups.group(1, 1),
                StompServerOption.builder().maxFrameLength(64 * 1024).build()
        ).webSocket("");

        try (Socket socket = new Socket()) {
            server.start();
            server.listen("localhost", 0).get(5, TimeUnit.SECONDS);
            int port = ((InetSocketAddress) server.connection().channel().localAddress()).getPort();
            socket.connect(new InetSocketAddress("localhost", port), 5_000);
            socket.setSoTimeout(5_000);
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            upgradeToWebSocket(out, in, "/");

            // Declare a 2 GiB text frame with a zero masking key, then send far less than that.
            out.write(new byte[]{
                    (byte) 0x81, (byte) 0xFF,
                    0x00, 0x00, 0x00, 0x00, 0x7F, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
                    0x00, 0x00, 0x00, 0x00
            });
            byte[] chunk = new byte[8 * 1024];
            Arrays.fill(chunk, (byte) 'A');
            try {
                for (int sent = 0; sent < 1024 * 1024; sent += chunk.length) {
                    out.write(chunk);
                }
                out.flush();
            } catch (IOException closedByServer) {
                // The server may close before every chunk is written.
            }

            assertThat(readUntilClosed(in)).isTrue();
        } finally {
            if (!server.isShutdown()) {
                server.shutdown();
            }
        }
    }

    @Test
    @Timeout(20)
    void accept_fragmented_message_from_jdk_websocket_client() throws Exception {
        StompServer server = StompServer.open(
                EventLoopGroups.group(1, 1),
                StompServerOption.builder().build()
        ).webSocket("/stomp");
        HttpClient httpClient = HttpClient.newHttpClient();
        WebSocket webSocket = null;

        try {
            server.start();
            server.listen("localhost", 0).get(5, TimeUnit.SECONDS);
            int port = ((InetSocketAddress) server.connection().channel().localAddress()).getPort();

            StringBuilder received = new StringBuilder();
            CompletableFuture<String> connected = new CompletableFuture<>();
            CompletableFuture<Integer> closed = new CompletableFuture<>();
            webSocket = httpClient.newWebSocketBuilder()
                    .subprotocols("v12.stomp")
                    .buildAsync(URI.create("ws://localhost:" + port + "/stomp"), new WebSocket.Listener() {
                        @Override
                        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
                            received.append(data);
                            if (received.indexOf("\0") >= 0) {
                                connected.complete(received.toString());
                            }
                            ws.request(1);
                            return null;
                        }

                        @Override
                        public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
                            closed.complete(statusCode);
                            return null;
                        }
                    })
                    .get(5, TimeUnit.SECONDS);

            webSocket.sendText("CONNECT\naccept-version:1.2\n", false).get(5, TimeUnit.SECONDS);
            webSocket.sendPing(ByteBuffer.wrap(new byte[]{'P'})).get(5, TimeUnit.SECONDS);
            webSocket.sendText("host:localhost\n\n\0", true).get(5, TimeUnit.SECONDS);
            assertThat(connected.get(5, TimeUnit.SECONDS)).startsWith("CONNECTED");

            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "").get(5, TimeUnit.SECONDS);
            assertThat(closed.get(5, TimeUnit.SECONDS)).isEqualTo(WebSocket.NORMAL_CLOSURE);
        } finally {
            if (webSocket != null) {
                webSocket.abort();
            }
            httpClient.shutdownNow();
            if (!server.isShutdown()) {
                server.shutdown();
            }
        }
    }

    @Test
    @Timeout(20)
    void send_normal_closure_after_disconnect() throws Exception {
        StompServer server = startWebSocketServer();
        HttpClient httpClient = HttpClient.newHttpClient();
        RecordingListener listener = new RecordingListener();
        WebSocket webSocket = null;

        try {
            webSocket = openJdkClient(httpClient, server, listener);
            webSocket.sendText("CONNECT\naccept-version:1.2\nhost:localhost\n\n\0", true).get(5, TimeUnit.SECONDS);
            assertThat(listener.messages.poll(5, TimeUnit.SECONDS)).startsWith("CONNECTED");

            webSocket.sendText("DISCONNECT\nreceipt:77\n\n\0", true).get(5, TimeUnit.SECONDS);

            assertThat(listener.messages.poll(5, TimeUnit.SECONDS)).startsWith("RECEIPT");
            assertThat(listener.closed.get(5, TimeUnit.SECONDS)).isEqualTo(WebSocket.NORMAL_CLOSURE);
        } finally {
            close(webSocket, httpClient, server);
        }
    }

    @Test
    @Timeout(20)
    void send_error_before_normal_closure_when_connect_is_rejected() throws Exception {
        StompServer server = startWebSocketServer();
        HttpClient httpClient = HttpClient.newHttpClient();
        RecordingListener listener = new RecordingListener();
        WebSocket webSocket = null;

        try {
            webSocket = openJdkClient(httpClient, server, listener);
            webSocket.sendText("CONNECT\naccept-version:1.0\nhost:localhost\n\n\0", true).get(5, TimeUnit.SECONDS);

            assertThat(listener.messages.poll(5, TimeUnit.SECONDS)).startsWith("ERROR");
            assertThat(listener.closed.get(5, TimeUnit.SECONDS)).isEqualTo(WebSocket.NORMAL_CLOSURE);
        } finally {
            close(webSocket, httpClient, server);
        }
    }

    @Test
    @Timeout(20)
    void send_normal_closure_when_server_shuts_down() throws Exception {
        StompServer server = startWebSocketServer();
        HttpClient httpClient = HttpClient.newHttpClient();
        RecordingListener listener = new RecordingListener();
        WebSocket webSocket = null;

        try {
            webSocket = openJdkClient(httpClient, server, listener);
            webSocket.sendText("CONNECT\naccept-version:1.2\nhost:localhost\n\n\0", true).get(5, TimeUnit.SECONDS);
            assertThat(listener.messages.poll(5, TimeUnit.SECONDS)).startsWith("CONNECTED");

            server.shutdown();

            assertThat(listener.closed.get(5, TimeUnit.SECONDS)).isEqualTo(WebSocket.NORMAL_CLOSURE);
        } finally {
            close(webSocket, httpClient, server);
        }
    }

    @Test
    @Timeout(20)
    void reply_to_client_close_with_one_close_frame() throws Exception {
        StompServer server = startWebSocketServer();

        try (Socket socket = new Socket()) {
            int port = ((InetSocketAddress) server.connection().channel().localAddress()).getPort();
            socket.connect(new InetSocketAddress("localhost", port), 5_000);
            socket.setSoTimeout(5_000);
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();
            upgradeToWebSocket(out, in, "/stomp");

            // Masked close frame with status 1000 and a zero masking key.
            out.write(new byte[]{(byte) 0x88, (byte) 0x82, 0x00, 0x00, 0x00, 0x00, 0x03, (byte) 0xE8});
            out.flush();

            assertThat(in.readAllBytes()).containsExactly((byte) 0x88, 0x02, 0x03, (byte) 0xE8);
        } finally {
            if (!server.isShutdown()) {
                server.shutdown();
            }
        }
    }

    @Test
    @Timeout(20)
    void deliver_non_utf8_body_to_websocket_subscriber_as_binary() throws Exception {
        StompServer server = startWebSocketServer();
        HttpClient httpClient = HttpClient.newHttpClient();
        RecordingListener subscriberListener = new RecordingListener();
        RecordingListener publisherListener = new RecordingListener();
        WebSocket subscriber = null;
        WebSocket publisher = null;

        try {
            subscriber = openJdkClient(httpClient, server, subscriberListener);
            subscriber.sendText("CONNECT\naccept-version:1.2\nhost:localhost\n\n\0", true).get(5, TimeUnit.SECONDS);
            assertThat(subscriberListener.messages.poll(5, TimeUnit.SECONDS)).startsWith("CONNECTED");
            subscriber.sendText("SUBSCRIBE\nid:0\ndestination:/topic/binary\nreceipt:1\n\n\0", true)
                    .get(5, TimeUnit.SECONDS);
            assertThat(subscriberListener.messages.poll(5, TimeUnit.SECONDS)).startsWith("RECEIPT");

            publisher = openJdkClient(httpClient, server, publisherListener);
            publisher.sendText("CONNECT\naccept-version:1.2\nhost:localhost\n\n\0", true).get(5, TimeUnit.SECONDS);
            assertThat(publisherListener.messages.poll(5, TimeUnit.SECONDS)).startsWith("CONNECTED");
            ByteArrayOutputStream send = new ByteArrayOutputStream();
            send.writeBytes("SEND\ndestination:/topic/binary\ncontent-length:1\n\n".getBytes(StandardCharsets.UTF_8));
            send.write(0xFF);
            send.write(0x00);
            publisher.sendBinary(ByteBuffer.wrap(send.toByteArray()), true).get(5, TimeUnit.SECONDS);

            byte[] message = subscriberListener.binaries.poll(5, TimeUnit.SECONDS);
            assertThat(message).isNotNull();
            assertThat(new String(message, StandardCharsets.ISO_8859_1)).startsWith("MESSAGE").endsWith("\n\u00FF\0");
        } finally {
            if (publisher != null) {
                publisher.abort();
            }
            close(subscriber, httpClient, server);
        }
    }

    @Test
    @Timeout(20)
    void close_with_invalid_payload_data_when_text_is_not_utf8() throws Exception {
        StompServer server = startWebSocketServer();

        try (Socket socket = new Socket()) {
            int port = ((InetSocketAddress) server.connection().channel().localAddress()).getPort();
            socket.connect(new InetSocketAddress("localhost", port), 5_000);
            socket.setSoTimeout(5_000);
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();
            upgradeToWebSocket(out, in, "/stomp");

            // Masked text frame carrying 0xFF, which never appears in UTF-8.
            out.write(new byte[]{(byte) 0x81, (byte) 0x81, 0x00, 0x00, 0x00, 0x00, (byte) 0xFF});
            out.flush();

            assertThat(in.readAllBytes()).containsExactly((byte) 0x88, 0x02, 0x03, (byte) 0xEF);
        } finally {
            if (!server.isShutdown()) {
                server.shutdown();
            }
        }
    }

    @Test
    @Timeout(20)
    void close_websocket_that_does_not_connect_in_time() throws Exception {
        StompServer server = startWebSocketServer(StompServerOption.builder().connectTimeoutMillis(200L).build());

        try (Socket socket = new Socket()) {
            int port = ((InetSocketAddress) server.connection().channel().localAddress()).getPort();
            socket.connect(new InetSocketAddress("localhost", port), 5_000);
            socket.setSoTimeout(5_000);
            upgradeToWebSocket(socket.getOutputStream(), socket.getInputStream(), "/stomp");

            String received = new String(socket.getInputStream().readAllBytes(), StandardCharsets.ISO_8859_1);
            assertThat(received).contains("ERROR").contains("message:CONNECT timeout");
        } finally {
            if (!server.isShutdown()) {
                server.shutdown();
            }
        }
    }

    @Test
    @Timeout(20)
    void close_websocket_that_does_not_finish_upgrade_in_time() throws Exception {
        StompServer server = startWebSocketServer(StompServerOption.builder().connectTimeoutMillis(200L).build());

        try (Socket socket = new Socket()) {
            int port = ((InetSocketAddress) server.connection().channel().localAddress()).getPort();
            socket.connect(new InetSocketAddress("localhost", port), 5_000);
            socket.setSoTimeout(5_000);
            socket.getOutputStream().write("GET /stomp HTTP/1.1\r\nHost: localhost\r\n".getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();

            assertThat(socket.getInputStream().read()).isEqualTo(-1);
        } finally {
            if (!server.isShutdown()) {
                server.shutdown();
            }
        }
    }

    private static StompServer startWebSocketServer() throws Exception {
        return startWebSocketServer(StompServerOption.builder().build());
    }

    private static StompServer startWebSocketServer(StompServerOption option) throws Exception {
        StompServer server = StompServer.open(
                EventLoopGroups.group(1, 1),
                option
        ).webSocket("/stomp");
        server.start();
        server.listen("localhost", 0).get(5, TimeUnit.SECONDS);
        return server;
    }

    private static WebSocket openJdkClient(HttpClient httpClient, StompServer server, RecordingListener listener)
            throws Exception {
        int port = ((InetSocketAddress) server.connection().channel().localAddress()).getPort();
        return httpClient.newWebSocketBuilder()
                .subprotocols("v12.stomp")
                .buildAsync(URI.create("ws://localhost:" + port + "/stomp"), listener)
                .get(5, TimeUnit.SECONDS);
    }

    private static void close(WebSocket webSocket, HttpClient httpClient, StompServer server) {
        if (webSocket != null) {
            webSocket.abort();
        }
        httpClient.shutdownNow();
        if (!server.isShutdown()) {
            server.shutdown();
        }
    }

    private static void upgradeToWebSocket(OutputStream out, InputStream in, String path) throws IOException {
        out.write(("GET " + path + " HTTP/1.1\r\n"
                + "Host: localhost\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n"
                + "Sec-WebSocket-Version: 13\r\n"
                + "Sec-WebSocket-Protocol: v12.stomp\r\n"
                + "\r\n").getBytes(StandardCharsets.US_ASCII));
        out.flush();
        assertThat(readHttpHead(in)).startsWith("HTTP/1.1 101");
    }

    private static String readHttpHead(InputStream in) throws IOException {
        StringBuilder head = new StringBuilder();
        while (head.indexOf("\r\n\r\n") < 0) {
            int next = in.read();
            if (next < 0) {
                throw new IOException("Connection closed during WebSocket handshake");
            }
            head.append((char) next);
        }
        return head.toString();
    }

    private static boolean readUntilClosed(InputStream in) throws IOException {
        byte[] discard = new byte[1024];
        try {
            while (in.read(discard) >= 0) {
                // Drain the STOMP ERROR and close frames until the server closes the socket.
            }
            return true;
        } catch (SocketTimeoutException stillOpen) {
            return false;
        } catch (SocketException reset) {
            return true;
        }
    }

    /**
     * Collects complete text messages and the close status received by a JDK WebSocket client.
     */
    private static final class RecordingListener implements WebSocket.Listener {

        private final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        private final BlockingQueue<byte[]> binaries = new LinkedBlockingQueue<>();
        private final CompletableFuture<Integer> closed = new CompletableFuture<>();
        private final StringBuilder partial = new StringBuilder();
        private final ByteArrayOutputStream partialBinary = new ByteArrayOutputStream();

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            partial.append(data);
            if (last) {
                messages.add(partial.toString());
                partial.setLength(0);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            byte[] bytes = new byte[data.remaining()];
            data.get(bytes);
            partialBinary.writeBytes(bytes);
            if (last) {
                binaries.add(partialBinary.toByteArray());
                partialBinary.reset();
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            closed.complete(statusCode);
            return null;
        }
    }
}
