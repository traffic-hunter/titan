package org.traffichunter.titan.monitor.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;
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

    private static HttpRequest request(int port, String path) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/titan" + path)).GET().build();
    }

    private static HttpRequest request(int port, String path, String token) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/titan" + path))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
    }

    private static int availablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
