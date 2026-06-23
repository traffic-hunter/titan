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

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.time.Duration;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.traffichunter.titan.core.channel.EventLoopGroups;
import org.traffichunter.titan.core.resilience.retry.RetryPolicy;
import org.traffichunter.titan.core.transport.option.InetServerOption;
import org.traffichunter.titan.core.transport.stomp.client.StompConnection;
import org.traffichunter.titan.core.transport.stomp.option.StompClientOption;
import org.traffichunter.titan.core.transport.stomp.option.StompServerOption;

@DisplayNameGeneration(ReplaceUnderscores.class)
class StompReconnectIntegrationTest {

    private static final String HOST = "127.0.0.1";
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 20;

    @Test
    @Timeout(value = 20, unit = SECONDS)
    void client_reconnects_after_server_restart() throws Exception {
        StompServer server = startServer(0);
        int port = localPort(server);
        TitanStompClient client = startClient(port);

        try {
            StompConnection initialConnection = client.connect().get(3, SECONDS);
            StompServer initialServer = server;
            await().atMost(10, SECONDS)
                    .untilAsserted(() -> assertThat(initialServer.connection().connections()).hasSize(1));

            server.shutdown(SHUTDOWN_TIMEOUT_SECONDS, SECONDS);
            await().atMost(10, SECONDS)
                    .untilAsserted(() -> assertThat(initialConnection.isConnected()).isFalse());

            server = startServer(port);

            StompServer restartedServer = server;
            await().atMost(10, SECONDS)
                    .untilAsserted(() -> {
                        assertThat(restartedServer.connection().connections()).hasSize(1);
                        assertThat(client.connection()).isNotSameAs(initialConnection);
                        assertThat(client.connection().isConnected()).isTrue();
                    });
        } finally {
            client.shutdown(SHUTDOWN_TIMEOUT_SECONDS, SECONDS);
            if (!server.isShutdown()) {
                server.shutdown(SHUTDOWN_TIMEOUT_SECONDS, SECONDS);
            }
        }
    }

    private static StompServer startServer(int port) throws Exception {
        InetServerOption inetOption = InetServerOption.builder()
                .reuseAddress(true)
                .childReuseAddress(true)
                .build();
        StompServerOption option = StompServerOption.builder()
                .inetServerOption(inetOption)
                .build();
        StompServer server = StompServer.open(EventLoopGroups.singleGroup(), option);
        server.start();
        server.listen(HOST, port).get(3, SECONDS);
        return server;
    }

    private static TitanStompClient startClient(int port) {
        StompClientOption option = StompClientOption.builder()
                .host(HOST)
                .port(port)
                .reconnectPolicy(RetryPolicy.fixed(
                        RetryPolicy.UNLIMITED_ATTEMPTS,
                        Duration.ofMillis(10)
                ))
                .build();
        TitanStompClient client = TitanStompClient.open(EventLoopGroups.singleGroup(), option);
        client.start();
        return client;
    }

    private static int localPort(StompServer server) {
        SocketAddress address = server.connection().channel().localAddress();
        if (address instanceof InetSocketAddress inetAddress) {
            return inetAddress.getPort();
        }
        throw new IllegalStateException("STOMP server has no local address");
    }
}
