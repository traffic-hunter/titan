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

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.traffichunter.titan.core.channel.EventLoopGroups;
import org.traffichunter.titan.core.net.JdkTlsContext;
import org.traffichunter.titan.core.net.TlsOptions;
import org.traffichunter.titan.core.net.TlsSide;
import org.traffichunter.titan.core.transport.InetServer;
import org.traffichunter.titan.core.transport.stomp.StompServer;
import org.traffichunter.titan.core.transport.stomp.option.StompServerOption;
import org.traffichunter.titan.core.transport.stomp.option.StompSessionOption;

/**
 * @author yun
 */
class ClientTlsIntegrationTest {

    private static final String PASSWORD = "changeit";
    private static final String DESTINATION = "/queue/tls";

    private TitanClient client;
    private StompServer server;

    @AfterEach
    void tearDown() {
        if (client != null) {
            client.shutdown(10, SECONDS);
        }
        if (server != null && !server.isShutdown()) {
            server.shutdown(5, SECONDS);
        }
    }

    @Test
    @Timeout(value = 30, unit = SECONDS)
    void exchange_messages_over_tls() throws Exception {
        int port = startServer(false);
        client = connect(TitanClient.builder().port(port));

        assertRoundTrip();
    }

    @Test
    @Timeout(value = 30, unit = SECONDS)
    void exchange_messages_over_wss() throws Exception {
        int port = startServer(true);
        client = connect(TitanClient.builder().port(port).webSocket("/stomp"));

        assertRoundTrip();
    }

    private void assertRoundTrip() throws Exception {
        LinkedBlockingQueue<String> received = new LinkedBlockingQueue<>();
        client.subscribe(DESTINATION, frame -> received.add(new String(frame.body()))).get(10, SECONDS);
        client.send(DESTINATION, "hello").get(10, SECONDS);

        assertThat(received.poll(10, SECONDS)).isEqualTo("hello");
    }

    private int startServer(boolean webSocket) throws Exception {
        EventLoopGroups groups = EventLoopGroups.group(1, 1);
        InetServer inetServer = InetServer.open(groups)
                .tls(new JdkTlsContext(tlsOptions(TlsSide.SERVER)));
        server = StompServer.open(groups, inetServer, StompServerOption.builder().build());
        if (webSocket) {
            server.webSocket("/stomp");
        }
        server.start();
        server.listen("localhost", 0).get(5, SECONDS);
        return ((InetSocketAddress) server.connection().channel().localAddress()).getPort();
    }

    private static TitanClient connect(TitanClient.Builder builder) throws Exception {
        TitanClient client = builder
                .host("localhost")
                .worker(1)
                .connectTimeout(Duration.ofSeconds(5))
                .session(StompSessionOption.builder().heartbeatX(0L).heartbeatY(0L).build())
                .tls(tlsOptions(TlsSide.CLIENT))
                .build();
        client.start();
        client.connect().get(10, SECONDS);
        return client;
    }

    private static TlsOptions tlsOptions(TlsSide side) throws Exception {
        return TlsOptions.builder()
                .side(side)
                .keyStore(testKeyStore(), "PKCS12", PASSWORD, PASSWORD)
                .build();
    }

    private static Path testKeyStore() throws Exception {
        URL resource = ClientTlsIntegrationTest.class.getResource("/tls/titan-test.p12");
        if (resource == null) {
            throw new AssertionError("Missing TLS test key store");
        }
        return Path.of(resource.toURI());
    }
}
