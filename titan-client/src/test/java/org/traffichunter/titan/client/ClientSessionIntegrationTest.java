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
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.traffichunter.titan.core.channel.EventLoopGroups;
import org.traffichunter.titan.core.codec.stomp.StompHeaders.Elements;
import org.traffichunter.titan.core.transport.stomp.StompServer;
import org.traffichunter.titan.core.transport.stomp.option.StompServerOption;
import org.traffichunter.titan.core.transport.stomp.option.StompSessionOption;
import org.traffichunter.titan.core.util.buffer.Buffer;

/**
 * Checks against a real server that what the session agreed to is what actually happens.
 *
 * @author yun
 */
class ClientSessionIntegrationTest {

    private static final String DESTINATION = "/queue/session";

    private final List<TitanClient> clients = new ArrayList<>();
    private StompServer server;

    @AfterEach
    void tearDown() throws Exception {
        for (TitanClient client : clients) {
            client.shutdown(10, SECONDS);
        }
        if (server != null && !server.isShutdown()) {
            server.shutdown(5, SECONDS);
        }
    }

    @Test
    @Timeout(value = 30, unit = SECONDS)
    void a_receipt_identifier_holding_a_colon_still_matches_its_request() throws Exception {
        int port = startServer();
        TitanClient client = connect(port);
        LinkedBlockingQueue<String> received = new LinkedBlockingQueue<>();

        // The identifier is escaped on the way out and has to be read back on the way in. Left
        // escaped, the RECEIPT no longer names the request that is waiting for it and the
        // subscribe never completes.
        String subscriptionId = client.subscribe(
                DESTINATION,
                Map.of(Elements.RECEIPT, "run-7:producer-2"),
                frame -> received.add(new String(frame.body()))
        ).get(10, SECONDS);
        client.send(DESTINATION, Buffer.heap().alloc("hello"), Map.of(Elements.RECEIPT, "run-7:message:1"))
                .get(10, SECONDS);

        assertThat(subscriptionId).isNotBlank();
        assertThat(received.poll(10, SECONDS)).isEqualTo("hello");
    }

    @Test
    @Timeout(value = 60, unit = SECONDS)
    void a_connection_that_only_publishes_is_not_closed_by_its_own_watchdog() throws Exception {
        int port = startServer();
        TitanClient client = connect(port);

        // Nothing comes back on a send without a receipt, so a client that policed an interval it
        // never asked for would close this connection about two seconds in.
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        int sent = 0;
        while (System.nanoTime() < deadline) {
            client.send(DESTINATION, Buffer.heap().alloc("tick")).get(5, SECONDS);
            sent++;
            Thread.sleep(20);
        }

        assertThat(sent).isGreaterThan(100);
        assertThat(client.isConnected()).isTrue();
    }

    private int startServer() throws Exception {
        server = StompServer.open(EventLoopGroups.group(1, 1), StompServerOption.builder().build());
        server.start();
        server.listen("localhost", 0).get(5, SECONDS);
        return ((InetSocketAddress) server.connection().channel().localAddress()).getPort();
    }

    private TitanClient connect(int port) throws Exception {
        TitanClient client = TitanClient.builder()
                .host("localhost")
                .port(port)
                .worker(1)
                .connectTimeout(Duration.ofSeconds(5))
                .session(StompSessionOption.builder().heartbeatX(0L).heartbeatY(0L).build())
                .build();
        clients.add(client);
        client.start();
        client.connect().get(10, TimeUnit.SECONDS);
        return client;
    }
}
