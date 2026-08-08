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
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.traffichunter.titan.core.channel.EventLoopGroups;
import org.traffichunter.titan.client.TitanClient;
import org.traffichunter.titan.core.transport.stomp.StompServer;
import org.traffichunter.titan.core.transport.stomp.option.StompServerOption;
import org.traffichunter.titan.core.transport.stomp.option.StompSessionOption;
import org.traffichunter.titan.core.util.buffer.Buffer;
import org.traffichunter.titan.dispatch.DispatchGateway;
import org.traffichunter.titan.dispatch.StompSendToFanoutHandler;
import org.traffichunter.titan.dispatch.exporter.StompDispatchExporter;

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
        DispatchGateway dispatchGateway = DispatchGateway.ofVirtual(new StompDispatchExporter(server.connection()));
        server.onStomp(handler -> handler.sendHandler(new StompSendToFanoutHandler(dispatchGateway)));

        TitanClient producer = null;
        TitanClient firstConsumer = null;
        TitanClient secondConsumer = null;

        try {
            server.start();
            server.listen(HOST, 0).get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            int port = boundPort(server);

            producer = newStompClient(2, port);
            firstConsumer = newStompClient(2, port);
            secondConsumer = newStompClient(2, port);

            producer.start();
            firstConsumer.start();
            secondConsumer.start();

            TitanClient producerConnection = connect(producer);
            TitanClient firstConsumerConnection = connect(firstConsumer);
            TitanClient secondConsumerConnection = connect(secondConsumer);

            firstConsumerConnection.subscribe(FANOUT_DESTINATION, Map.of(ID, "smoke-fanout-first"), frame -> {
                firstPayload.set(new String(frame.body(), StandardCharsets.UTF_8));
                received.countDown();
            }).get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);

            secondConsumerConnection.subscribe(FANOUT_DESTINATION, Map.of(ID, "smoke-fanout-second"), frame -> {
                secondPayload.set(new String(frame.body(), StandardCharsets.UTF_8));
                received.countDown();
            }).get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);

            producerConnection.send(FANOUT_DESTINATION, Buffer.heap().alloc(payload))
                    .get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);

            assertThat(received.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(firstPayload.get()).isEqualTo(payload);
            assertThat(secondPayload.get()).isEqualTo(payload);
        } finally {
            shutdown(secondConsumer);
            shutdown(firstConsumer);
            shutdown(producer);
            dispatchGateway.close();
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

    private static TitanClient newStompClient(int workers, int port) {
        return TitanClient.builder()
                .worker(workers)
                .host(HOST)
                .port(port)
                .session(StompSessionOption.builder()
                        .heartbeatX(0L)
                        .heartbeatY(0L)
                        .build())
                .build();
    }

    private static TitanClient connect(TitanClient client) throws Exception {
        return client.connect()
                .get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
    }

    private static int boundPort(StompServer server) {
        SocketAddress localAddress = server.connection().channel().localAddress();
        assertThat(localAddress).isInstanceOf(InetSocketAddress.class);
        Assertions.assertNotNull(localAddress);
        return ((InetSocketAddress) localAddress).getPort();
    }

    private static void shutdown(TitanClient client) {
        if (client != null && client.isStarted()) {
            client.shutdown(10, TimeUnit.SECONDS);
        }
    }
}
