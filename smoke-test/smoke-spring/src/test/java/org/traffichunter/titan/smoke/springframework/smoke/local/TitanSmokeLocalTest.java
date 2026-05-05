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
package org.traffichunter.titan.smoke.springframework.smoke.local;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.traffichunter.titan.core.channel.EventLoopGroups;
import org.traffichunter.titan.core.channel.stomp.StompClientConnection;
import org.traffichunter.titan.core.codec.stomp.StompHeaders;
import org.traffichunter.titan.core.transport.stomp.StompClient;
import org.traffichunter.titan.core.transport.stomp.option.StompClientOption;
import org.traffichunter.titan.core.util.buffer.Buffer;
import org.traffichunter.titan.smoke.springframework.smoke.junit.LocalSmokeTest;
import org.traffichunter.titan.springframework.stomp.TitanProperties;
import org.traffichunter.titan.springframework.stomp.TitanTemplate;

import static org.traffichunter.titan.core.codec.stomp.StompHeaders.Elements.ID;

/**
 * @author yun
 */
@LocalSmokeTest
public class TitanSmokeLocalTest {

    private static final String PAYLOAD = "smoke-message";
    private static final String FANOUT_DESTINATION = "/topic/smoke-local/fanout";

    @Autowired
    private TitanTemplate titanTemplate;

    @Autowired
    private TitanProperties titanProperties;


    @Test
    void sync_subscribe_and_send_smoke() throws Exception {
        String destination = destination("sync");
        subscribeEventually(destination);
        sendEventually(destination, PAYLOAD);
    }

    @Test
    void sync_send_bytes_smoke() throws Exception {
        String destination = destination("bytes");
        byte[] payload = PAYLOAD.getBytes();

        subscribeEventually(destination);

        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .ignoreExceptions()
                .untilAsserted(() -> assertThat(titanTemplate.send(destination, payload)).isNotNull());
    }

    @Test
    void repeated_send_smoke() throws Exception {
        String destination = destination("repeat");
        subscribeEventually(destination);

        sendEventually(destination, PAYLOAD + "-1");
        sendEventually(destination, PAYLOAD + "-2");
    }

    @Test
    void subscribe_multiple_destinations_smoke() throws Exception {
        String destinationA = destination("multi-a");
        String destinationB = destination("multi-b");

        subscribeEventually(destinationA);
        subscribeEventually(destinationB);

        sendEventually(destinationA, PAYLOAD + "-a");
        sendEventually(destinationB, PAYLOAD + "-b");
    }

    @Test
    void producer_send_should_be_received_by_subscribed_consumers() throws Exception {
        String payload = PAYLOAD + "-fanout";
        CountDownLatch received = new CountDownLatch(2);
        AtomicReference<String> firstPayload = new AtomicReference<>();
        AtomicReference<String> secondPayload = new AtomicReference<>();

        EventLoopGroups producerGroups = EventLoopGroups.group(1, 2);
        EventLoopGroups firstConsumerGroups = EventLoopGroups.group(1, 2);
        EventLoopGroups secondConsumerGroups = EventLoopGroups.group(1, 2);

        StompClient producer = newStompClient(producerGroups);
        StompClient firstConsumer = newStompClient(firstConsumerGroups);
        StompClient secondConsumer = newStompClient(secondConsumerGroups);

        try {
            producer.start();
            firstConsumer.start();
            secondConsumer.start();

            StompClientConnection producerConnection = connect(producer);
            StompClientConnection firstConsumerConnection = connect(firstConsumer);
            StompClientConnection secondConsumerConnection = connect(secondConsumer);

            StompHeaders firstSubscribeHeaders = StompHeaders.create();
            firstSubscribeHeaders.put(ID, "smoke-fanout");
            firstConsumerConnection.subscribe(FANOUT_DESTINATION, firstSubscribeHeaders, frame -> {
                firstPayload.set(frame.getBody().toString(StandardCharsets.UTF_8));
                received.countDown();
            }).get(titanProperties.getConnectTimeoutMillis(), TimeUnit.MILLISECONDS);

            StompHeaders secondSubscribeHeaders = StompHeaders.create();
            secondSubscribeHeaders.put(ID, "smoke-fanout");
            secondConsumerConnection.subscribe(FANOUT_DESTINATION, secondSubscribeHeaders, frame -> {
                secondPayload.set(frame.getBody().toString(StandardCharsets.UTF_8));
                received.countDown();
            }).get(titanProperties.getConnectTimeoutMillis(), TimeUnit.MILLISECONDS);

            producerConnection.send(FANOUT_DESTINATION, Buffer.alloc(payload))
                    .get(titanProperties.getConnectTimeoutMillis(), TimeUnit.MILLISECONDS);

            assertThat(received.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(firstPayload.get()).isEqualTo(payload);
            assertThat(secondPayload.get()).isEqualTo(payload);
        } finally {
            producer.shutdown();
            firstConsumer.shutdown();
            secondConsumer.shutdown();
        }

    }

    @Test
    void unsubscribe_and_resubscribe_smoke() throws Exception {
        String destination = destination("unsubscribe");

        subscribeEventually(destination);
        unsubscribeEventually(destination);
        subscribeEventually(destination);
        sendEventually(destination, PAYLOAD + "-after-unsubscribe");
    }

    @Test
    void byte_buffer_send_smoke() throws Exception {
        String destination = destination("byte-buffer");
        subscribeEventually(destination);

        ByteBuffer wrapped = ByteBuffer.wrap((PAYLOAD + "-wrapped").getBytes(StandardCharsets.UTF_8));
        ByteBuffer direct = ByteBuffer.allocateDirect((PAYLOAD + "-direct").getBytes(StandardCharsets.UTF_8).length);
        direct.put((PAYLOAD + "-direct").getBytes(StandardCharsets.UTF_8));
        direct.flip();

        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .ignoreExceptions()
                .untilAsserted(() -> assertThat(titanTemplate.send(destination, wrapped)).isNotNull());

        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .ignoreExceptions()
                .untilAsserted(() -> assertThat(titanTemplate.send(destination, direct)).isNotNull());
    }

    @Test
    void invalid_destination_should_fail_fast() {
        assertThatThrownBy(() -> titanTemplate.subscribe("/queue/smoke local/invalid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid routing key");
    }

    private static String destination(String suffix) {
        return "/queue/smoke-local/" + suffix;
    }

    private void subscribeEventually(String destination) {
        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .ignoreExceptions()
                .untilAsserted(() -> assertThat(titanTemplate.subscribe(destination)).isNotNull());
    }

    private void sendEventually(String destination, String payload) {
        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .ignoreExceptions()
                .untilAsserted(() -> assertThat(titanTemplate.send(destination, payload)).isNotNull());
    }

    private void unsubscribeEventually(String destination) {
        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .ignoreExceptions()
                .untilAsserted(() -> assertThat(titanTemplate.unsubscribe(destination)).isNotNull());
    }

    private StompClient newStompClient(EventLoopGroups groups) {
        return StompClient.open(groups, StompClientOption.builder()
                .host(titanProperties.getHost())
                .port(titanProperties.getPort())
                .login(titanProperties.getLogin())
                .passcode(titanProperties.getPasscode())
                .virtualHost(titanProperties.getVirtualHost())
                .heartbeatX(titanProperties.getHeartbeatX())
                .heartbeatY(titanProperties.getHeartbeatY())
                .maxFrameLength(titanProperties.getMaxFrameLength())
                .autoComputeContentLength(titanProperties.isAutoComputeContentLength())
                .useStompFrame(titanProperties.isUseStompFrame())
                .bypassHostHeader(titanProperties.isBypassHostHeader())
                .build());
    }

    private StompClientConnection connect(StompClient client) throws Exception {
        return client.connect(
                titanProperties.getHost(),
                titanProperties.getPort(),
                titanProperties.getConnectTimeoutMillis(),
                TimeUnit.MILLISECONDS
        ).get(titanProperties.getConnectTimeoutMillis(), TimeUnit.MILLISECONDS);
    }
}
