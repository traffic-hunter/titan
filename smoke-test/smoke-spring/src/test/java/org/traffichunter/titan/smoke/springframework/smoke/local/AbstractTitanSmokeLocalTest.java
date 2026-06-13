package org.traffichunter.titan.smoke.springframework.smoke.local;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.traffichunter.titan.core.codec.stomp.StompFrames;
import org.traffichunter.titan.core.transport.stomp.client.StompClient;
import org.traffichunter.titan.core.transport.stomp.client.StompConnection;
import org.traffichunter.titan.springframework.stomp.core.TitanClientManager;
import org.traffichunter.titan.springframework.stomp.core.TitanTemplate;
import org.traffichunter.titan.springframework.stomp.listener.TitanListenerEndpointRegistry;

public abstract class AbstractTitanSmokeLocalTest {

    private static final String PAYLOAD = "smoke-message";

    @Autowired
    private TitanTemplate titanTemplate;

    @Autowired
    private TitanClientManager clientManager;

    @Autowired
    private StompClient stompClient;

    @Autowired
    private SmokeListener smokeListener;

    @Autowired
    private TitanListenerEndpointRegistry listenerRegistry;

    protected abstract Class<? extends StompClient> clientType();

    protected abstract Class<? extends StompConnection> connectionType();

    @Test
    void configured_client_matches_smoke_client() throws Exception {
        assertThat(stompClient).isInstanceOf(clientType());
        assertThat(clientManager.connection()).isInstanceOf(connectionType());
    }

    @Test
    void sync_subscribe_and_send_smoke() throws Exception {
        String destination = destination("sync");
        BlockingQueue<String> received = subscribeReceivingEventually(destination);
        sendEventually(destination, PAYLOAD);
        assertReceived(received, PAYLOAD);
    }

    @RepeatedTest(5)
    void subscribe_send_receive_contract_is_stable() throws Exception {
        String destination = destination("stable");
        String payload = PAYLOAD + "-stable-" + UUID.randomUUID();

        BlockingQueue<String> received = subscribeReceivingEventually(destination);
        sendEventually(destination, payload);
        assertReceived(received, payload);
    }

    @Test
    void sync_send_bytes_smoke() throws Exception {
        String destination = destination("bytes");
        byte[] payload = PAYLOAD.getBytes();

        BlockingQueue<String> received = subscribeReceivingEventually(destination);

        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .ignoreExceptions()
                .untilAsserted(() -> assertThat(titanTemplate.send(destination, payload)).isNotNull());
        assertReceived(received, PAYLOAD);
    }

    @Test
    void repeated_send_smoke() throws Exception {
        String destination = destination("repeat");
        BlockingQueue<String> received = subscribeReceivingEventually(destination);

        sendEventually(destination, PAYLOAD + "-1");
        sendEventually(destination, PAYLOAD + "-2");
        assertReceived(received, PAYLOAD + "-1");
        assertReceived(received, PAYLOAD + "-2");
    }

    @Test
    void subscribe_multiple_destinations_smoke() throws Exception {
        String destinationA = destination("multi-a");
        String destinationB = destination("multi-b");

        BlockingQueue<String> receivedA = subscribeReceivingEventually(destinationA);
        BlockingQueue<String> receivedB = subscribeReceivingEventually(destinationB);

        sendEventually(destinationA, PAYLOAD + "-a");
        sendEventually(destinationB, PAYLOAD + "-b");
        assertReceived(receivedA, PAYLOAD + "-a");
        assertReceived(receivedB, PAYLOAD + "-b");
    }

    @Test
    void unsubscribe_and_resubscribe_smoke() throws Exception {
        String destination = destination("unsubscribe");

        subscribeReceivingEventually(destination);
        unsubscribeEventually(destination);
        BlockingQueue<String> received = subscribeReceivingEventually(destination);
        sendEventually(destination, PAYLOAD + "-after-unsubscribe");
        assertReceived(received, PAYLOAD + "-after-unsubscribe");
    }

    @Test
    void byte_buffer_send_smoke() throws Exception {
        String destination = destination("byte-buffer");
        BlockingQueue<String> received = subscribeReceivingEventually(destination);

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
        assertReceived(received, PAYLOAD + "-wrapped");
        assertReceived(received, PAYLOAD + "-direct");
    }

    @Test
    void titan_listener_receives_message_smoke() {
        String payload = PAYLOAD + "-listener";

        smokeListener.clear();
        awaitListenerReady();
        sendEventually(SmokeListener.DESTINATION, payload);

        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(smokeListener.received()).contains(payload));
    }

    @Test
    void invalid_destination_should_fail_fast() {
        assertThatThrownBy(() -> titanTemplate.subscribe("/queue/smoke local/invalid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid routing key");
    }

    private static String destination(String suffix) {
        return "/queue/smoke-local/" + suffix + "/" + UUID.randomUUID();
    }

    private BlockingQueue<String> subscribeReceivingEventually(String destination) {
        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .ignoreExceptions()
                .untilAsserted(() -> assertThat(clientManager.connection()
                        .subscribe(destination, frame -> received.add(body(frame)))
                        .get(clientManager.connectTimeoutMillis(), TimeUnit.MILLISECONDS)
                ).isNotNull());
        String probe = PAYLOAD + "-probe-" + UUID.randomUUID();
        sendEventually(destination, probe);
        assertReceived(received, probe);
        received.clear();
        return received;
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

    private static void assertReceived(BlockingQueue<String> received, String payload) {
        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(received).contains(payload));
    }

    private void awaitListenerReady() {
        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(listenerRegistry.isRunning()).isTrue());

        String probe = PAYLOAD + "-listener-probe-" + UUID.randomUUID();
        sendEventually(SmokeListener.DESTINATION, probe);
        assertReceived(smokeListener.received(), probe);
        smokeListener.clear();
    }

    private static String body(StompFrames frame) {
        return new String(frame.body(), StandardCharsets.UTF_8);
    }
}
