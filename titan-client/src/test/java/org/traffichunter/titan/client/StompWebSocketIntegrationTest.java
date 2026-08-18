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
package org.traffichunter.titan.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.traffichunter.titan.core.channel.EventLoopGroups;
import org.traffichunter.titan.core.channel.stomp.StompClientWebSocketChannel;
import org.traffichunter.titan.core.transport.stomp.StompServer;
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
    void connect_vertx_stomp_client_over_websocket() throws Exception {
        StompServer server = StompServer.open(
                EventLoopGroups.group(1, 1),
                StompServerOption.builder().build()
        ).webSocket("/stomp");
        try {
            server.start();
            server.listen("localhost", 0).get(5, TimeUnit.SECONDS);
            int port = ((InetSocketAddress) server.connection().channel().localAddress()).getPort();
            ClientConfiguration configuration = ClientConfiguration.builder()
                    .host("localhost")
                    .port(port)
                    .webSocket("/stomp")
                    .build();
            VertxStompClientDriver driver = new VertxStompClientDriver(configuration);
            DefaultTitanClient client = new DefaultTitanClient(driver);

            try {
                client.start();
                client.connect().get(5, TimeUnit.SECONDS);

                assertThat(client.isConnected()).isTrue();
                assertThat(driver.channel().isConnected()).isTrue();
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

}
