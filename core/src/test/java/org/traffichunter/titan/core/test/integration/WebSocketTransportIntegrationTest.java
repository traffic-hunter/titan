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
package org.traffichunter.titan.core.test.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.traffichunter.titan.core.channel.ChannelInBoundHandler;
import org.traffichunter.titan.core.channel.ChannelInBoundHandlerChain;
import org.traffichunter.titan.core.channel.EventLoopGroups;
import org.traffichunter.titan.core.channel.NetChannel;
import org.traffichunter.titan.core.channel.websocket.WebSocketChannel;
import org.traffichunter.titan.core.transport.InetClient;
import org.traffichunter.titan.core.transport.InetServer;
import org.traffichunter.titan.core.transport.websocket.WebSocketClient;
import org.traffichunter.titan.core.util.Protocol;
import org.traffichunter.titan.core.util.buffer.Buffer;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static java.nio.charset.StandardCharsets.US_ASCII;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * @author yun
 */
class WebSocketTransportIntegrationTest {

    private static final String UPGRADE_REQUEST = "GET /stomp HTTP/1.1\r\n"
            + "Host: localhost\r\n"
            + "Upgrade: websocket\r\n"
            + "Connection: Upgrade\r\n"
            + "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n"
            + "Sec-WebSocket-Version: 13\r\n"
            + "\r\n";

    private InetServer server;
    private WebSocketClient client;

    @AfterEach
    void tearDown() {
        if (client != null && !client.isShutdown()) {
            client.shutdown();
        }
        if (server != null && !server.isShutdown()) {
            server.shutdown();
        }
    }

    @Test
    @Timeout(10)
    void exchange_payload_after_websocket_upgrade() throws Exception {
        LinkedBlockingQueue<String> serverMessages = new LinkedBlockingQueue<>();
        LinkedBlockingQueue<String> clientMessages = new LinkedBlockingQueue<>();

        server = InetServer.open(EventLoopGroups.group(1, 1))
                .upgradeWebSocket("/stomp")
                .onChannel(channel -> channel.chain().add(echo(serverMessages)));
        server.start();
        server.listen("localhost", 0).get(5, TimeUnit.SECONDS);

        int port = ((InetSocketAddress) server.localAddress()).getPort();
        client = InetClient.open(EventLoopGroups.group(1))
                .upgradeWebSocket(Protocol.STOMP, "/stomp");
        client.start();

        WebSocketChannel channel = client.connect("localhost", port, 5, TimeUnit.SECONDS)
                .get(5, TimeUnit.SECONDS);
        channel.chain().add(capture(clientMessages));

        client.send(Buffer.heap().alloc("hello websocket")).get(5, TimeUnit.SECONDS);

        assertThat(serverMessages.poll(5, TimeUnit.SECONDS)).isEqualTo("hello websocket");
        assertThat(clientMessages.poll(5, TimeUnit.SECONDS)).isEqualTo("echo:hello websocket");
    }

    @Test
    @Timeout(10)
    void answer_unsupported_version_with_upgrade_required() throws Exception {
        int port = startServer();

        String response = exchange(port, UPGRADE_REQUEST.replace("Sec-WebSocket-Version: 13", "Sec-WebSocket-Version: 8"));

        assertThat(response).startsWith("HTTP/1.1 426 Upgrade Required\r\n");
        assertThat(response).contains("Sec-WebSocket-Version: 13\r\n");
    }

    @Test
    @Timeout(10)
    void answer_plain_http_request_with_bad_request() throws Exception {
        int port = startServer();

        String response = exchange(port, "GET /stomp HTTP/1.1\r\nHost: localhost\r\n\r\n");

        assertThat(response).startsWith("HTTP/1.1 400 Bad Request\r\n");
    }

    @Test
    @Timeout(10)
    void answer_oversized_head_with_request_header_fields_too_large() throws Exception {
        int port = startServer();

        String response = exchange(port, "GET /stomp HTTP/1.1\r\nX-Padding: " + "a".repeat(9000) + "\r\n");

        assertThat(response).startsWith("HTTP/1.1 431 Request Header Fields Too Large\r\n");
    }

    @Test
    @Timeout(10)
    void jdk_client_receives_not_found_for_unknown_path() throws Exception {
        int port = startServer();
        HttpClient httpClient = HttpClient.newHttpClient();

        try {
            Throwable error = catchThrowable(() -> httpClient.newWebSocketBuilder()
                    .buildAsync(URI.create("ws://localhost:" + port + "/unknown"), new WebSocket.Listener() { })
                    .join());

            assertThat(error).isInstanceOf(CompletionException.class);
            assertThat(error.getCause()).isInstanceOf(WebSocketHandshakeException.class);
            assertThat(((WebSocketHandshakeException) error.getCause()).getResponse().statusCode()).isEqualTo(404);
        } finally {
            httpClient.shutdownNow();
        }
    }

    private int startServer() throws Exception {
        server = InetServer.open(EventLoopGroups.group(1, 1))
                .upgradeWebSocket("/stomp")
                .onChannel(channel -> { });
        server.start();
        server.listen("localhost", 0).get(5, TimeUnit.SECONDS);
        return ((InetSocketAddress) server.localAddress()).getPort();
    }

    /**
     * Sends a raw request and reads the response until the server closes the connection.
     */
    private static String exchange(int port, String request) throws Exception {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("localhost", port), 5_000);
            socket.setSoTimeout(5_000);
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();
            out.write(request.getBytes(US_ASCII));
            out.flush();
            return new String(in.readAllBytes(), US_ASCII);
        }
    }

    private static ChannelInBoundHandler echo(LinkedBlockingQueue<String> messages) {
        return new ChannelInBoundHandler() {
            @Override
            public void sparkChannelRead(
                    NetChannel channel,
                    Buffer buffer,
                    ChannelInBoundHandlerChain chain
            ) {
                try {
                    String message = buffer.toString();
                    messages.add(message);
                    channel.writeAndFlush(Buffer.heap().alloc("echo:" + message));
                } finally {
                    buffer.release();
                }
            }
        };
    }

    private static ChannelInBoundHandler capture(LinkedBlockingQueue<String> messages) {
        return new ChannelInBoundHandler() {
            @Override
            public void sparkChannelRead(
                    NetChannel channel,
                    Buffer buffer,
                    ChannelInBoundHandlerChain chain
            ) {
                try {
                    messages.add(buffer.toString());
                } finally {
                    buffer.release();
                }
            }
        };
    }
}
