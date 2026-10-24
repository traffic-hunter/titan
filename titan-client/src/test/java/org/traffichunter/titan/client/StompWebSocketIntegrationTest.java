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
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
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

            out.write(("GET / HTTP/1.1\r\n"
                    + "Host: localhost\r\n"
                    + "Upgrade: websocket\r\n"
                    + "Connection: Upgrade\r\n"
                    + "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n"
                    + "Sec-WebSocket-Version: 13\r\n"
                    + "Sec-WebSocket-Protocol: v12.stomp\r\n"
                    + "\r\n").getBytes(StandardCharsets.US_ASCII));
            out.flush();
            assertThat(readHttpHead(in)).startsWith("HTTP/1.1 101");

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
    void accept_jdk_websocket_client() throws Exception {
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

            webSocket.sendText("CONNECT\naccept-version:1.2\nhost:localhost\n\n\0", true)
                    .get(5, TimeUnit.SECONDS);
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

}
