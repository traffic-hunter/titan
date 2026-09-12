package org.traffichunter.titan.monitor.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.traffichunter.titan.core.message.Message;
import org.traffichunter.titan.core.util.Destination;
import org.traffichunter.titan.dispatch.DispatcherQueue;
import org.traffichunter.titan.dispatch.DispatcherQueueManagers;
import org.traffichunter.titan.monitor.MonitoringSnapshotService;

/**
 * Covers the {@code group} parameter of the queue endpoint: which queues a read returns and
 * which queue a change reaches.
 */
class MonitoringQueueGroupTest {

    private static final String DESTINATION = "/queue/group-http";
    private static final String TOKEN = "secret";

    private final HttpClient client = HttpClient.newHttpClient();
    private TestQueueManager manager;
    private MonitoringHttpServer server;
    private int port;

    @BeforeEach
    void startServer() throws Exception {
        manager = new TestQueueManager();
        DispatcherQueueManagers.register("group-test", manager);

        port = availablePort();
        server = MonitoringHttpServer.builder(new MonitoringSnapshotService("group-test"))
                .host("127.0.0.1")
                .port(port)
                .token(TOKEN)
                .build();
        Thread thread = new Thread(server::start, "monitor-http-queue-group-test");
        thread.setDaemon(true);
        thread.start();
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(send(get("")).statusCode()).isEqualTo(200));
    }

    @AfterEach
    void stopServer() {
        // Queue MBeans are process-wide, so every queue this test made has to go.
        for (String group : new String[] {"default", "market", "notification"}) {
            DispatcherQueue queue = manager.queue(group, Destination.create(DESTINATION));
            if (queue != null) {
                manager.deleteQueue(group, Destination.create(DESTINATION), true);
            }
        }
        DispatcherQueueManagers.unregister("group-test");
        server.close();
    }

    @Test
    void a_read_without_a_group_lists_every_group() throws Exception {
        createQueue("default");
        createQueue("market");

        String body = send(get("")).body();

        assertThat(body).contains("\"group\":\"default\"", "\"group\":\"market\"");
    }

    @Test
    void a_read_with_a_group_lists_only_that_group() throws Exception {
        createQueue("default");
        createQueue("market");

        String body = send(get("&group=market")).body();

        assertThat(body).contains("\"group\":\"market\"");
        assertThat(body).doesNotContain("\"group\":\"default\"");
    }

    @Test
    void a_read_of_a_group_with_no_queues_is_an_empty_list() throws Exception {
        createQueue("market");

        HttpResponse<String> response = send(get("&group=unused"));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("[]");
    }

    @Test
    void a_read_with_a_malformed_group_is_refused() throws Exception {
        HttpResponse<String> response = send(get("&group=bad%2Fname"));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("Invalid group name");
    }

    @Test
    void creation_puts_the_queue_in_the_named_group_only() throws Exception {
        HttpResponse<String> response = send(post("&group=market&maxPendingBytes=2048"));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"group\":\"market\"");
        assertThat(manager.queue("market", Destination.create(DESTINATION))).isNotNull();
        assertThat(manager.queue("default", Destination.create(DESTINATION))).isNull();
    }

    @Test
    void creation_without_a_group_uses_the_default_group() throws Exception {
        HttpResponse<String> response = send(post("&maxPendingBytes=2048"));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"group\":\"default\"");
        assertThat(manager.queue("default", Destination.create(DESTINATION))).isNotNull();
    }

    @Test
    void creation_keeps_the_byte_limit_of_a_queue_that_already_exists() throws Exception {
        send(post("&group=market&maxPendingBytes=2048"));

        HttpResponse<String> again = send(post("&group=market&maxPendingBytes=9999"));

        assertThat(again.body()).contains("\"maxPendingBytes\":2048");
    }

    @Test
    void an_action_reaches_the_named_group_and_reports_it() throws Exception {
        createQueue("default");
        createQueue("market");

        HttpResponse<String> response = send(post("&group=market&action=pause"));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"group\":\"market\"");
        assertThat(manager.queue("market", Destination.create(DESTINATION)).isPaused()).isTrue();
        assertThat(manager.queue("default", Destination.create(DESTINATION)).isPaused()).isFalse();
    }

    @Test
    void a_purge_leaves_the_other_group_holding_its_messages() throws Exception {
        enqueue(createQueue("default"));
        enqueue(createQueue("market"));

        assertThat(send(post("&group=market&action=purge")).statusCode()).isEqualTo(200);

        assertThat(manager.queue("market", Destination.create(DESTINATION)).size()).isZero();
        assertThat(manager.queue("default", Destination.create(DESTINATION)).size()).isEqualTo(1);
    }

    @Test
    void an_action_on_an_unknown_group_is_not_found_and_creates_nothing() throws Exception {
        HttpResponse<String> response = send(post("&group=unused&action=pause"));

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(manager.queue("unused", Destination.create(DESTINATION))).isNull();
    }

    @Test
    void an_action_with_a_malformed_group_is_refused_before_anything_changes() throws Exception {
        createQueue("default");

        HttpResponse<String> response = send(post("&group=bad%2Fname&action=pause"));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(manager.queue("default", Destination.create(DESTINATION)).isPaused()).isFalse();
    }

    @Test
    void a_delete_removes_the_named_group_queue_only() throws Exception {
        createQueue("default");
        createQueue("market");

        HttpResponse<String> response = send(delete("&group=market"));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(manager.queue("market", Destination.create(DESTINATION))).isNull();
        assertThat(manager.queue("default", Destination.create(DESTINATION))).isNotNull();
    }

    @Test
    void a_non_empty_group_queue_needs_force_and_the_retry_keeps_the_group() throws Exception {
        enqueue(createQueue("market"));

        assertThat(send(delete("&group=market")).statusCode()).isEqualTo(409);
        assertThat(manager.queue("market", Destination.create(DESTINATION))).isNotNull();

        assertThat(send(delete("&group=market&force=true")).statusCode()).isEqualTo(200);
        assertThat(manager.queue("market", Destination.create(DESTINATION))).isNull();
    }

    @Test
    void a_delete_of_a_destination_that_exists_in_another_group_is_not_found() throws Exception {
        createQueue("market");

        HttpResponse<String> response = send(delete(""));

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(manager.queue("market", Destination.create(DESTINATION))).isNotNull();
    }

    private DispatcherQueue createQueue(String group) {
        return manager.createQueue(group, Destination.create(DESTINATION), 4096);
    }

    private static void enqueue(DispatcherQueue queue) {
        queue.enqueue(Message.builder()
                .group(queue.getGroup())
                .destination(Destination.create(DESTINATION))
                .createdAt(Instant.now())
                .producerId("test")
                .body("payload".getBytes(StandardCharsets.UTF_8))
                .build());
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest get(String query) {
        return authorized(HttpRequest.newBuilder(uri(query))).GET().build();
    }

    private HttpRequest post(String query) {
        return authorized(HttpRequest.newBuilder(uri(query)))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
    }

    private HttpRequest delete(String query) {
        return authorized(HttpRequest.newBuilder(uri(query))).DELETE().build();
    }

    private static HttpRequest.Builder authorized(HttpRequest.Builder builder) {
        return builder.header("Authorization", "Bearer " + TOKEN);
    }

    private URI uri(String query) {
        return URI.create("http://127.0.0.1:" + port + "/titan"
                + MonitoringHttpServer.QUEUES_PATH
                + "?destination=" + URLEncoder.encode(DESTINATION, StandardCharsets.UTF_8)
                + query);
    }

    private static int availablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
