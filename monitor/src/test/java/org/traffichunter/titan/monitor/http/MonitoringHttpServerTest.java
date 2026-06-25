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

import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;
import org.traffichunter.titan.core.message.Message;
import org.traffichunter.titan.core.message.dispatcher.*;
import org.traffichunter.titan.core.util.Destination;
import org.traffichunter.titan.core.util.buffer.Buffer;
import org.traffichunter.titan.core.util.mbeans.DispatcherQueueMbeans;
import org.traffichunter.titan.monitor.MonitoringSnapshotService;

class MonitoringHttpServerTest {

    @Test
    void exposes_health_and_snapshot_endpoints() throws Exception {
        int port = availablePort();
        MonitoringHttpServer server = MonitoringHttpServer.builder(new MonitoringSnapshotService("test"))
                .host("127.0.0.1")
                .port(port)
                .build();
        Thread thread = new Thread(server::start, "monitor-http-test");
        thread.setDaemon(true);
        thread.start();

        try {
            HttpClient client = HttpClient.newHttpClient();
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                HttpResponse<String> health = client.send(
                        request(port, MonitoringHttpServer.HEALTH_PATH),
                        HttpResponse.BodyHandlers.ofString()
                );
                assertThat(health.statusCode()).isEqualTo(200);
            });

            HttpResponse<String> snapshot = client.send(
                    request(port, MonitoringHttpServer.SNAPSHOT_PATH),
                    HttpResponse.BodyHandlers.ofString()
            );

            assertThat(snapshot.statusCode()).isEqualTo(200);
            assertThat(snapshot.body()).contains("\"server\"", "\"jvm\"", "\"queues\"");
        } finally {
            server.close();
        }
    }

    @Test
    void rejects_snapshot_without_configured_token() throws Exception {
        int port = availablePort();
        MonitoringHttpServer server = MonitoringHttpServer.builder(new MonitoringSnapshotService("test"))
                .host("127.0.0.1")
                .port(port)
                .token("secret")
                .build();
        Thread thread = new Thread(server::start, "monitor-http-auth-test");
        thread.setDaemon(true);
        thread.start();

        try {
            HttpClient client = HttpClient.newHttpClient();
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                HttpResponse<String> health = client.send(
                        request(port, MonitoringHttpServer.HEALTH_PATH),
                        HttpResponse.BodyHandlers.ofString()
                );
                assertThat(health.statusCode()).isEqualTo(401);
            });
        } finally {
            server.close();
        }
    }

    @Test
    void allows_snapshot_with_configured_bearer_token() throws Exception {
        int port = availablePort();
        MonitoringHttpServer server = MonitoringHttpServer.builder(new MonitoringSnapshotService("test"))
                .host("127.0.0.1")
                .port(port)
                .token("secret")
                .build();
        Thread thread = new Thread(server::start, "monitor-http-auth-success-test");
        thread.setDaemon(true);
        thread.start();

        try {
            HttpClient client = HttpClient.newHttpClient();
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                HttpResponse<String> snapshot = client.send(
                        request(port, MonitoringHttpServer.SNAPSHOT_PATH, "secret"),
                        HttpResponse.BodyHandlers.ofString()
                );
                assertThat(snapshot.statusCode()).isEqualTo(200);
                assertThat(snapshot.body()).contains("\"server\"", "\"jvm\"", "\"queues\"");
            });
        } finally {
            server.close();
        }
    }

    @Test
    void rejects_queue_mutation_when_token_is_not_configured() throws Exception {
        int port = availablePort();
        MonitoringHttpServer server = MonitoringHttpServer.builder(new MonitoringSnapshotService("test"))
                .host("127.0.0.1")
                .port(port)
                .build();
        Thread thread = new Thread(server::start, "monitor-http-queue-no-token-test");
        thread.setDaemon(true);
        thread.start();

        try {
            HttpClient client = HttpClient.newHttpClient();
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                HttpResponse<String> response = client.send(
                        postQueue(port, "/queue/no-token", null),
                        HttpResponse.BodyHandlers.ofString()
                );
                assertThat(response.statusCode()).isEqualTo(403);
            });
        } finally {
            server.close();
        }
    }

    @Test
    void rejects_queue_mutation_without_bearer_token() throws Exception {
        int port = availablePort();
        MonitoringHttpServer server = MonitoringHttpServer.builder(new MonitoringSnapshotService("test"))
                .host("127.0.0.1")
                .port(port)
                .token("secret")
                .build();
        Thread thread = new Thread(server::start, "monitor-http-queue-auth-test");
        thread.setDaemon(true);
        thread.start();

        try {
            HttpClient client = HttpClient.newHttpClient();
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                HttpResponse<String> response = client.send(
                        postQueue(port, "/queue/missing-token", null),
                        HttpResponse.BodyHandlers.ofString()
                );
                assertThat(response.statusCode()).isEqualTo(401);
            });
        } finally {
            server.close();
        }
    }

    @Test
    void creates_and_deletes_queue_with_configured_bearer_token() throws Exception {
        DispatcherQueueManagers.register("test", new TestQueueManager());
        int port = availablePort();
        MonitoringHttpServer server = MonitoringHttpServer.builder(new MonitoringSnapshotService("test"))
                .host("127.0.0.1")
                .port(port)
                .token("secret")
                .build();
        Thread thread = new Thread(server::start, "monitor-http-queue-success-test");
        thread.setDaemon(true);
        thread.start();

        try {
            HttpClient client = HttpClient.newHttpClient();
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                HttpResponse<String> created = client.send(
                        postQueue(port, "/queue/orders", "secret"),
                        HttpResponse.BodyHandlers.ofString()
                );
                assertThat(created.statusCode()).isEqualTo(200);
                assertThat(created.body()).contains("/queue/orders");
            });

            HttpResponse<String> deleted = client.send(
                    deleteQueue(port, "/queue/orders", false, "secret"),
                    HttpResponse.BodyHandlers.ofString()
            );

            assertThat(deleted.statusCode()).isEqualTo(200);
            assertThat(deleted.body()).contains("deleted");
        } finally {
            DispatcherQueueManagers.unregister("test");
            server.close();
        }
    }

    @Test
    void rejects_non_empty_queue_delete_until_force_is_used() throws Exception {
        TestQueueManager manager = new TestQueueManager();
        DispatcherQueue queue = manager.createQueue(Destination.create("/queue/non-empty"), 10);
        queue.enqueue(Message.builder()
                .destination(Destination.create("/queue/non-empty"))
                .createdAt(java.time.Instant.now())
                .producerId("test")
                .body(Buffer.alloc("test".getBytes()))
                .build());
        DispatcherQueueManagers.register("test", manager);

        int port = availablePort();
        MonitoringHttpServer server = MonitoringHttpServer.builder(new MonitoringSnapshotService("test"))
                .host("127.0.0.1")
                .port(port)
                .token("secret")
                .build();
        Thread thread = new Thread(server::start, "monitor-http-queue-conflict-test");
        thread.setDaemon(true);
        thread.start();

        try {
            HttpClient client = HttpClient.newHttpClient();
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                HttpResponse<String> conflict = client.send(
                        deleteQueue(port, "/queue/non-empty", false, "secret"),
                        HttpResponse.BodyHandlers.ofString()
                );
                assertThat(conflict.statusCode()).isEqualTo(409);
            });

            HttpResponse<String> deleted = client.send(
                    deleteQueue(port, "/queue/non-empty", true, "secret"),
                    HttpResponse.BodyHandlers.ofString()
            );

            assertThat(deleted.statusCode()).isEqualTo(200);
        } finally {
            DispatcherQueueManagers.unregister("test");
            server.close();
        }
    }

    @Test
    void returns_not_found_when_deleting_missing_queue() throws Exception {
        DispatcherQueueManagers.register("test", new TestQueueManager());

        int port = availablePort();
        MonitoringHttpServer server = MonitoringHttpServer.builder(new MonitoringSnapshotService("test"))
                .host("127.0.0.1")
                .port(port)
                .token("secret")
                .build();
        Thread thread = new Thread(server::start, "monitor-http-queue-missing-test");
        thread.setDaemon(true);
        thread.start();

        try {
            HttpClient client = HttpClient.newHttpClient();
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                HttpResponse<String> missing = client.send(
                        deleteQueue(port, "/queue/missing", false, "secret"),
                        HttpResponse.BodyHandlers.ofString()
                );
                assertThat(missing.statusCode()).isEqualTo(404);
            });
        } finally {
            DispatcherQueueManagers.unregister("test");
            server.close();
        }
    }

    private static HttpRequest request(int port, String path) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/titan" + path)).GET().build();
    }

    private static HttpRequest request(int port, String path, String token) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/titan" + path))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
    }

    private static HttpRequest postQueue(int port, String destination, String token) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(queueUri(port, destination, false))
                .POST(HttpRequest.BodyPublishers.noBody());
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return builder.build();
    }

    private static HttpRequest deleteQueue(int port, String destination, boolean force, String token) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(queueUri(port, destination, force))
                .DELETE();
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return builder.build();
    }

    private static URI queueUri(int port, String destination, boolean force) {
        String encoded = URLEncoder.encode(destination, StandardCharsets.UTF_8);
        return URI.create("http://127.0.0.1:" + port + "/titan"
                + MonitoringHttpServer.QUEUES_PATH
                + "?destination=" + encoded
                + "&capacity=10"
                + "&force=" + force);
    }

    private static int availablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    @NullMarked
    private static final class TestQueueManager implements DispatcherQueueManager {

        private final Dispatcher dispatcher = new TrieDispatcher();

        @Override
        public DispatcherQueue createQueue(Destination destination, int capacity) {
            return dispatcher.getOrPut(destination, capacity);
        }

        @Override
        public DispatcherQueueDeleteResult deleteQueue(Destination destination, boolean force) {
            DispatcherQueue queue = dispatcher.get(destination);
            if (queue == null) {
                return new DispatcherQueueDeleteResult(DispatcherQueueDeleteResult.Status.NOT_FOUND, 0);
            }
            int size = queue.size();
            if (size > 0 && !force) {
                return new DispatcherQueueDeleteResult(DispatcherQueueDeleteResult.Status.NOT_EMPTY, size);
            }
            if (force) {
                queue.clear();
            }
            dispatcher.remove(destination);
            DispatcherQueueMbeans.unregister(queue.getDestination());
            return new DispatcherQueueDeleteResult(DispatcherQueueDeleteResult.Status.DELETED, size);
        }
    }
}
