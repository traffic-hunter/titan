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

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.traffichunter.titan.client.TitanClient;
import org.traffichunter.titan.core.resilience.retry.RetryPolicy;
import org.traffichunter.titan.core.transport.stomp.option.StompSessionOption;

/**
 * Controls the packaged Titan server used by one smoke test.
 *
 * <p>The server runs in its own JVM, so startup, configuration loading, SPI wiring, shutdown
 * hooks, and the assembled distribution are exercised together.</p>
 *
 * @author yun
 */
final class TitanRuntime implements AutoCloseable {

    private static final String HOST = "127.0.0.1";
    private static final String CONFIGURATION_RESOURCE = "/titan-smoke.yml";
    private static final Duration START_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration STOP_TIMEOUT = Duration.ofSeconds(10);

    private final List<TitanClient> clients = new ArrayList<>();
    private final Path directory;
    private final Path configuration;
    private final Path log;
    private Process process;
    private TitanSmokeTransport transport;
    private int maxFrameLength;
    private int port;

    TitanRuntime() {
        try {
            directory = Files.createTempDirectory("titan-smoke-");
            configuration = directory.resolve("titan-env.yml");
            log = directory.resolve("titan.log");
        } catch (IOException error) {
            throw new IllegalStateException("Failed to create Titan smoke directory", error);
        }
    }

    void start(TitanSmokeTransport transport) throws Exception {
        start(transport, 1_048_576);
    }

    void start(TitanSmokeTransport transport, int maxFrameLength) throws Exception {
        if (isRunning()) {
            throw new IllegalStateException("Titan server is already running");
        }
        this.transport = transport;
        this.maxFrameLength = maxFrameLength;
        if (port == 0) {
            port = availablePort();
        }

        Files.writeString(configuration, configuration(transport, maxFrameLength));
        Path jar = Path.of(System.getProperty("titan.smoke.jar", ""));
        if (!Files.isRegularFile(jar)) {
            throw new IllegalStateException("Packaged Titan server not found: " + jar);
        }

        Path java = Path.of(System.getProperty("java.home"), "bin", "java");
        process = new ProcessBuilder(
                java.toString(),
                "-Dtitan.environment.path=" + configuration,
                "-Dtitan.banner.mode=off",
                "-jar",
                jar.toString()
        ).redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile()))
                .start();

        awaitReady();
    }

    void restart() throws Exception {
        if (transport == null) {
            throw new IllegalStateException("Titan server has not been configured");
        }
        stop();
        start(transport, maxFrameLength);
    }

    TitanClient client() throws Exception {
        if (!isRunning()) {
            throw new IllegalStateException("Titan server is not running");
        }

        TitanClient.Builder builder = TitanClient.builder()
                .host(HOST)
                .port(port)
                .worker(1)
                .connectTimeout(Duration.ofSeconds(5))
                .reconnect(RetryPolicy.fixed(RetryPolicy.UNLIMITED_ATTEMPTS, Duration.ofMillis(100)))
                .session(StompSessionOption.builder().heartbeatX(0L).heartbeatY(0L).build());
        if (transport != null && transport.isWebSocket()) {
            builder.webSocket("/");
        }

        TitanClient client = builder.build();
        clients.add(client);
        client.start();
        client.connect().get(10, TimeUnit.SECONDS);
        return client;
    }

    int port() {
        return port;
    }

    boolean isRunning() {
        return process != null && process.isAlive();
    }

    boolean stop() throws InterruptedException {
        Process current = process;
        if (current == null || !current.isAlive()) {
            process = null;
            return true;
        }

        current.destroy();
        boolean stopped = current.waitFor(STOP_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        if (!stopped) {
            current.destroyForcibly();
            stopped = current.waitFor(STOP_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        }
        process = null;
        return stopped;
    }

    String logs() {
        try {
            return Files.exists(log) ? Files.readString(log) : "Titan process did not produce a log.";
        } catch (IOException error) {
            return "Failed to read Titan process log: " + error.getMessage();
        }
    }

    @Override
    public void close() throws Exception {
        Exception failure = null;
        for (TitanClient client : clients) {
            try {
                client.shutdown(5, TimeUnit.SECONDS);
            } catch (Exception error) {
                failure = append(failure, error);
            }
        }
        clients.clear();

        try {
            if (!stop()) {
                failure = append(failure, new IllegalStateException("Titan process did not terminate"));
            }
        } catch (Exception error) {
            failure = append(failure, error);
        }

        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (Exception error) {
            failure = append(failure, error);
        }

        if (failure != null) {
            throw failure;
        }
    }

    private void awaitReady() throws Exception {
        long deadline = System.nanoTime() + START_TIMEOUT.toNanos();
        IOException connectionFailure = null;
        while (System.nanoTime() < deadline) {
            Process current = process;
            if (current == null || !current.isAlive()) {
                throw new IllegalStateException("Titan process exited during startup:\n" + logs());
            }
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(HOST, port), 200);
                return;
            } catch (IOException error) {
                connectionFailure = error;
                Thread.sleep(50);
            }
        }
        throw new IllegalStateException("Titan server did not listen within " + START_TIMEOUT + ":\n" + logs(),
                connectionFailure);
    }

    private String configuration(TitanSmokeTransport transport, int maxFrameLength) throws IOException {
        String template;
        try (var input = TitanRuntime.class.getResourceAsStream(CONFIGURATION_RESOURCE)) {
            if (input == null) {
                throw new IOException("Smoke configuration resource not found: " + CONFIGURATION_RESOURCE);
            }
            template = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        return template
                .replace("${transport}", transport.setting())
                .replace("${host}", HOST)
                .replace("${port}", Integer.toString(port))
                .replace("${maxFrameLength}", Integer.toString(maxFrameLength));
    }

    private static int availablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        }
    }

    private static Exception append(Exception failure, Exception error) {
        if (failure == null) {
            return error;
        }
        failure.addSuppressed(error);
        return failure;
    }
}
