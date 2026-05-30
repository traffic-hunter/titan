package org.traffichunter.titan.smoke.springframework.smoke.local;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.traffichunter.titan.core.transport.stomp.client.StompClient;
import org.traffichunter.titan.core.transport.stomp.client.StompClientOperations;
import org.traffichunter.titan.springframework.stomp.TitanClientManager;
import org.traffichunter.titan.springframework.stomp.TitanTemplate;

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

    protected abstract Class<? extends StompClient> clientType();

    protected abstract Class<? extends StompClientOperations> operationsType();

    @Test
    void configured_client_matches_smoke_client() throws Exception {
        assertThat(stompClient).isInstanceOf(clientType());
        assertThat(clientManager.operations()).isInstanceOf(operationsType());
    }

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
    void titan_listener_receives_message_smoke() {
        String payload = PAYLOAD + "-listener";

        smokeListener.clear();
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
}
