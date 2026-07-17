/*
The MIT License

Copyright (c) 2025 traffic-hunter

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
*/
package org.traffichunter.titan.core.transport.stomp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.traffichunter.titan.core.channel.EventLoopGroups;
import org.traffichunter.titan.core.channel.stomp.StompClientWebSocketChannel;
import org.traffichunter.titan.core.transport.stomp.option.StompClientOption;
import org.traffichunter.titan.core.transport.stomp.option.StompServerOption;
import org.traffichunter.titan.core.transport.websocket.WebSocketHandshakeException;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author yun
 */
class StompWebSocketIntegrationTest {

    @Test
    @Timeout(10)
    void connect_stomp_client_over_websocket() throws Exception {
        StompServer server = StompServer.open(
                EventLoopGroups.group(1, 1),
                StompServerOption.builder().build()
        ).upgradeWebsocket("/stomp");
        TitanStompClient client = TitanStompClient.open(
                EventLoopGroups.group(1, 1),
                StompClientOption.builder().build()
        ).upgradeWebsocket("/stomp");

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
            if (!client.isShutdown()) {
                client.shutdown();
            }
            if (!server.isShutdown()) {
                server.shutdown();
            }
        }
    }

    @Test
    @Timeout(10)
    void connect_vertx_stomp_client_over_websocket() throws Exception {
        StompServer server = StompServer.open(
                EventLoopGroups.group(1, 1),
                StompServerOption.builder().build()
        ).upgradeWebsocket("/stomp");
        try {
            server.start();
            server.listen("localhost", 0).get(5, TimeUnit.SECONDS);
            int port = ((InetSocketAddress) server.connection().channel().localAddress()).getPort();
            VertxStompClient client = VertxStompClient.open(
                    StompClientOption.builder().host("localhost").port(port).build()
            ).upgradeWebsocket("/stomp");

            try {
                client.start();
                client.connect().get(5, TimeUnit.SECONDS);

                assertThat(client.connection().isConnected()).isTrue();
                assertThat(client.channel().isConnected()).isTrue();
                assertThat(server.connection().connections()).hasSize(1);
            } finally {
                if (!client.isShutdown()) {
                    client.shutdown(5, TimeUnit.SECONDS);
                }
            }
        } finally {
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
        ).upgradeWebsocket("/stomp");
        TitanStompClient client = TitanStompClient.open(
                EventLoopGroups.group(1, 1),
                StompClientOption.builder().build()
        ).upgradeWebsocket("/wrong");

        try {
            server.start();
            server.listen("localhost", 0).get(5, TimeUnit.SECONDS);
            int port = ((InetSocketAddress) server.connection().channel().localAddress()).getPort();
            client.start();

            assertThatThrownBy(() -> client.connect("localhost", port).get(5, TimeUnit.SECONDS))
                    .hasRootCauseInstanceOf(WebSocketHandshakeException.class);
            assertThat(server.connection().connections()).isEmpty();
        } finally {
            if (!client.isShutdown()) {
                client.shutdown();
            }
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

            assertThatThrownBy(() -> server.upgradeWebsocket("/stomp"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("after start");
        } finally {
            server.shutdown();
        }
    }

    @Test
    void reject_client_transport_change_after_start() {
        TitanStompClient client = TitanStompClient.open(
                EventLoopGroups.group(1, 1),
                StompClientOption.builder().build()
        );

        try {
            client.start();

            assertThatThrownBy(() -> client.upgradeWebsocket("/stomp"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("after start");
        } finally {
            client.shutdown();
        }
    }

    @Test
    void reject_invalid_websocket_paths() {
        StompServer server = StompServer.open(
                EventLoopGroups.group(1, 1),
                StompServerOption.builder().build()
        );
        TitanStompClient client = TitanStompClient.open(
                EventLoopGroups.group(1, 1),
                StompClientOption.builder().build()
        );

        assertThatThrownBy(() -> server.upgradeWebsocket("stomp"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("start with '/'");
        assertThatThrownBy(() -> client.upgradeWebsocket(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("start with '/'");
    }
}
