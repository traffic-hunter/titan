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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.traffichunter.titan.core.channel.EventLoopGroups;
import org.traffichunter.titan.core.transport.stomp.StompServer;
import org.traffichunter.titan.core.transport.stomp.option.StompServerOption;

/**
 * @author yun
 */
class StompConnectGateIntegrationTest {

    @Test
    @Timeout(20)
    void reject_frame_sent_before_connect() throws Exception {
        StompServer server = startServer(StompServerOption.builder().build());

        try (Socket socket = connect(server)) {
            write(socket, "SEND\ndestination:/topic/a\n\nhello\0");

            assertThat(readUntilClosed(socket)).startsWith("ERROR").contains("message:Not connected");
        } finally {
            server.shutdown();
        }
    }

    @Test
    @Timeout(20)
    void close_connection_that_does_not_connect_in_time() throws Exception {
        StompServer server = startServer(StompServerOption.builder().connectTimeoutMillis(200L).build());

        try (Socket socket = connect(server)) {
            assertThat(readUntilClosed(socket)).startsWith("ERROR").contains("message:CONNECT timeout");
        } finally {
            server.shutdown();
        }
    }

    @Test
    @Timeout(20)
    void keep_connected_session_open_after_connect_timeout() throws Exception {
        StompServer server = startServer(StompServerOption.builder().connectTimeoutMillis(200L).build());

        try (Socket socket = connect(server)) {
            write(socket, "CONNECT\naccept-version:1.2\nhost:localhost\nheart-beat:0,0\n\n\0");
            assertThat(readFrame(socket)).startsWith("CONNECTED");

            Thread.sleep(500);
            write(socket, "SUBSCRIBE\nid:0\ndestination:/topic/a\nreceipt:1\n\n\0");

            assertThat(readFrame(socket)).startsWith("RECEIPT");
        } finally {
            server.shutdown();
        }
    }

    @Test
    @Timeout(20)
    void keep_idle_connection_open_when_connect_timeout_is_disabled() throws Exception {
        StompServer server = startServer(StompServerOption.builder().connectTimeoutMillis(0L).build());

        try (Socket socket = connect(server)) {
            socket.setSoTimeout(500);

            assertThatThrownBy(() -> socket.getInputStream().read()).isInstanceOf(SocketTimeoutException.class);
        } finally {
            server.shutdown();
        }
    }

    private static StompServer startServer(StompServerOption option) throws Exception {
        StompServer server = StompServer.open(EventLoopGroups.group(1, 1), option);
        server.start();
        server.listen("localhost", 0).get(5, TimeUnit.SECONDS);
        return server;
    }

    private static Socket connect(StompServer server) throws IOException {
        int port = ((InetSocketAddress) server.connection().channel().localAddress()).getPort();
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress("localhost", port), 5_000);
        socket.setSoTimeout(5_000);
        return socket;
    }

    private static void write(Socket socket, String frame) throws IOException {
        OutputStream out = socket.getOutputStream();
        out.write(frame.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private static String readFrame(Socket socket) throws IOException {
        InputStream in = socket.getInputStream();
        StringBuilder frame = new StringBuilder();
        int next;
        while ((next = in.read()) > 0) {
            frame.append((char) next);
        }
        return frame.toString().stripLeading();
    }

    private static String readUntilClosed(Socket socket) throws IOException {
        return new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8).stripLeading();
    }
}
