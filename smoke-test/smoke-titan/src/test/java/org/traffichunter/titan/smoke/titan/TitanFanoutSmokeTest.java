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
package org.traffichunter.titan.smoke.titan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.traffichunter.titan.core.codec.stomp.StompHeaders.Elements.ID;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.traffichunter.titan.core.channel.EventLoopGroups;
import org.traffichunter.titan.core.channel.stomp.StompClientConnection;
import org.traffichunter.titan.core.codec.stomp.StompHeaders;
import org.traffichunter.titan.core.transport.stomp.StompClient;
import org.traffichunter.titan.core.transport.stomp.StompServer;
import org.traffichunter.titan.core.transport.stomp.option.StompClientOption;
import org.traffichunter.titan.core.transport.stomp.option.StompServerOption;
import org.traffichunter.titan.core.util.buffer.Buffer;
import org.traffichunter.titan.fanout.FanoutGateway;
import org.traffichunter.titan.fanout.StompSendToFanoutHandler;
import org.traffichunter.titan.fanout.exporter.StompFanoutExporter;

class TitanFanoutSmokeTest {

    private static final String HOST = "127.0.0.1";
    private static final String PAYLOAD = "smoke-message";
    private static final String FANOUT_DESTINATION = "/topic/smoke-titan/fanout";
    private static final long TIMEOUT_MILLIS = 10_000L;

    @Test
    void producer_send_should_be_received_by_subscribed_consumers() throws Exception {
        String payload = PAYLOAD + "-fanout";
        CountDownLatch received = new CountDownLatch(2);
        AtomicReference<String> firstPayload = new AtomicReference<>();
        AtomicReference<String> secondPayload = new AtomicReference<>();

        EventLoopGroups serverGroups = EventLoopGroups.group(1, 2);
        StompServer server = StompServer.open(serverGroups, stompServerOption());
        FanoutGateway fanoutGateway = FanoutGateway.ofVirtual(new StompFanoutExporter(server.connection()));
        server.onStomp(handler -> handler.sendHandler(new StompSendToFanoutHandler(fanoutGateway)));

        EventLoopGroups producerGroups = EventLoopGroups.group(1, 2);
        EventLoopGroups firstConsumerGroups = EventLoopGroups.group(1, 2);
        EventLoopGroups secondConsumerGroups = EventLoopGroups.group(1, 2);
        StompClient producer = null;
        StompClient firstConsumer = null;
        StompClient secondConsumer = null;

        try {
            server.start();
            server.listen(HOST, 0).get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            int port = boundPort(server);

            producer = newStompClient(producerGroups, port);
            firstConsumer = newStompClient(firstConsumerGroups, port);
            secondConsumer = newStompClient(secondConsumerGroups, port);

            producer.start();
            firstConsumer.start();
            secondConsumer.start();

            StompClientConnection producerConnection = connect(producer, port);
            StompClientConnection firstConsumerConnection = connect(firstConsumer, port);
            StompClientConnection secondConsumerConnection = connect(secondConsumer, port);

            StompHeaders firstSubscribeHeaders = StompHeaders.create();
            firstSubscribeHeaders.put(ID, "smoke-fanout-first");
            firstConsumerConnection.subscribe(FANOUT_DESTINATION, firstSubscribeHeaders, frame -> {
                firstPayload.set(frame.getBody().toString(StandardCharsets.UTF_8));
                received.countDown();
            }).get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);

            StompHeaders secondSubscribeHeaders = StompHeaders.create();
            secondSubscribeHeaders.put(ID, "smoke-fanout-second");
            secondConsumerConnection.subscribe(FANOUT_DESTINATION, secondSubscribeHeaders, frame -> {
                secondPayload.set(frame.getBody().toString(StandardCharsets.UTF_8));
                received.countDown();
            }).get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);

            producerConnection.send(FANOUT_DESTINATION, Buffer.alloc(payload))
                    .get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);

            assertThat(received.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(firstPayload.get()).isEqualTo(payload);
            assertThat(secondPayload.get()).isEqualTo(payload);
        } finally {
            shutdown(secondConsumer);
            shutdown(firstConsumer);
            shutdown(producer);
            fanoutGateway.close();
            if (server.isStart()) {
                server.shutdown(10, TimeUnit.SECONDS);
            }
        }
    }

    private static StompServerOption stompServerOption() {
        return StompServerOption.builder()
                .heartbeatX(0L)
                .heartbeatY(0L)
                .build();
    }

    private static StompClient newStompClient(EventLoopGroups groups, int port) {
        return StompClient.open(groups, StompClientOption.builder()
                .host(HOST)
                .port(port)
                .heartbeatX(0L)
                .heartbeatY(0L)
                .build());
    }

    private static StompClientConnection connect(StompClient client, int port) throws Exception {
        return client.connect(HOST, port, TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
                .get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
    }

    private static int boundPort(StompServer server) {
        SocketAddress localAddress = server.connection().channel().localAddress();
        assertThat(localAddress).isInstanceOf(InetSocketAddress.class);
        Assertions.assertNotNull(localAddress);
        return ((InetSocketAddress) localAddress).getPort();
    }

    private static void shutdown(StompClient client) {
        if (client != null && client.isStart()) {
            client.shutdown(10, TimeUnit.SECONDS);
        }
    }
}
