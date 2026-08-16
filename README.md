<p align="center">
  <img src=".github/assets/titan-logo-transparent.png" alt="Titan" width="160">
</p>

# Titan

[![Release](https://img.shields.io/github/v/release/traffic-hunter/titan)](https://github.com/traffic-hunter/titan/releases)
[![Maven Central](https://img.shields.io/maven-central/v/org.traffichunter.titan/titan-stomp)](https://central.sonatype.com/artifact/org.traffichunter.titan/titan-stomp)
[![CI](https://github.com/traffic-hunter/titan/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/traffic-hunter/titan/actions/workflows/ci.yml)

Titan is a lightweight message dispatch platform for real-time STOMP messaging
over TCP, WebSocket, and TLS. It provides an in-memory destination queue,
fanout delivery, reconnecting Java clients, Spring Boot integration, and local
runtime monitoring in a standalone JVM process.

Use Titan for notifications, chat-style messaging, telemetry fanout, and live
interaction backends where messages can be handled in memory. Titan is not an
in-process event bus or a durable broker replacement.

## Why Titan?

- **STOMP without a large broker stack.** Run a standalone JAR and connect using
  TCP or WebSocket.
- **One client API.** `TitanClient` hides the native and Vert.x implementations
  behind the same messaging contract.
- **Connection recovery.** The client reconnects after an unexpected disconnect
  and restores active subscriptions.
- **Spring-native usage.** Send with `TitanTemplate` and receive with
  `@TitanListener`.
- **Built-in visibility.** Inspect JVM and destination queue state through the
  local monitor API or terminal CLI.

Choose Titan when lightweight, in-memory STOMP dispatch is the goal. Choose a
durable broker such as Kafka or RabbitMQ when persistence, replicated logs,
clustering, or guaranteed recovery across server restarts is required.

## Quick Start

### 1. Download the server

Titan requires JDK 21 or newer. Download the standalone JAR from
[GitHub Releases](https://github.com/traffic-hunter/titan/releases), or use:

```bash
curl -LO https://github.com/traffic-hunter/titan/releases/download/0.8.0/titan-server-0.8.0.jar
```

### 2. Create `titan-env.yml`

```yaml
titan:
  monitor:
    enabled: true
    host: 127.0.0.1
    port: 7777
  servers:
    - name: stomp-dispatch
      protocol: stomp
      host: 0.0.0.0
      port: 61613
      protocol-options:
        supported-versions: "1.2"
        fanout-mode: "virtual"
```

### 3. Start Titan

```bash
java -Dtitan.environment.path=./titan-env.yml \
  -jar titan-server-0.8.0.jar
```

The STOMP server now listens on `localhost:61613`. Verify the node through the
monitor endpoint:

```bash
curl http://localhost:7777/titan/monitor/health
curl http://localhost:7777/titan/monitor/snapshot
```

Continue with the [Java client](./docs/examples/client.md) or
[Spring Boot client](./docs/examples/spring-client.md) to subscribe and send a
message. The complete walkthrough is available in the
[Quickstart guide](./docs/quickstart.md).

## Installation

Titan artifacts are published to Maven Central.

```kotlin
repositories {
    mavenCentral()
}
```

For a Spring Boot application:

```kotlin
implementation("org.traffichunter.titan:titan-spring-client:0.8.0")
```

For a standalone Java client:

```kotlin
implementation("org.traffichunter.titan:titan-client:0.8.0")
```

Low-level server and extension artifacts:

| Artifact | Purpose |
| --- | --- |
| `titan-stomp` | STOMP server and low-level transport APIs |
| `titan-dispatch` | Destination routing and fanout delivery |
| `titan-monitor` | Local HTTP monitoring server |
| `titan-bootstrap` | Standalone runtime bootstrap |
| `titan-core` | Core transport and runtime primitives |

Use the same `0.8.0` version for each artifact. The native client is selected
by default; call `implementation(TitanClient.Implementation.VERTX)` on the
client builder to select Vert.x without changing the messaging API.

## Basic Commands

```bash
# Start the standalone server
java -Dtitan.environment.path=./titan-env.yml -jar titan-server-0.8.0.jar

# Check health and inspect a snapshot
curl http://localhost:7777/titan/monitor/health
curl http://localhost:7777/titan/monitor/snapshot

# Build and test from source
./gradlew build
./gradlew test

# Build the standalone server JAR from source
./gradlew :bootstrap:shadowJar
```

Prebuilt releases also contain the terminal monitor CLI:

```bash
tar -xzf titan-cli-0.8.0-linux-amd64.tar.gz
./titan --addr http://localhost:7777
./titan --addr http://localhost:7777 --view queues
./titan --addr http://localhost:7777 queue list
```

See [Monitoring and CLI](./docs/operate/monitoring.md) for queue management,
authentication, and platform-specific archives.

## Documentation

- [Quickstart](./docs/quickstart.md)
- [How Titan works](./docs/concepts/how-titan-works.md)
- [Java client](./docs/examples/client.md)
- [Spring Boot client](./docs/examples/spring-client.md)
- [Server embedding](./docs/examples/server.md)
- [Configuration, WebSocket, and TLS](./docs/reference/configuration.md)
- [Monitoring and CLI](./docs/operate/monitoring.md)

## Support

- Report bugs and request features through
  [GitHub Issues](https://github.com/traffic-hunter/titan/issues).
- Use an issue to discuss a proposed contribution before starting a large
  change.
- Check existing issues and documentation before opening a new report. Include
  the Titan version, JDK version, transport, configuration, and relevant logs
  when reporting a runtime problem.

## Project Scope

- STOMP over TCP and WebSocket is the primary protocol surface.
- Dispatch and fanout state is currently held in memory.
- Monitoring focuses on local JVM, channel, and destination queue visibility.
- Durable storage, clustering, and replicated delivery are not current runtime
  guarantees.

## Development

Requirements:

- JDK 21+
- Gradle wrapper (`./gradlew`)
- Go 1.22+ for `titan-cli`

Run the smoke suites separately when changing transport or Spring lifecycle
behavior:

```bash
./gradlew :smoke-test:smoke-spring:test
./gradlew :smoke-test:smoke-titan:test
```

## Contributors

Thanks to everyone who has contributed to Titan.

<a href="https://github.com/traffic-hunter/titan/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=traffic-hunter/titan" alt="Titan contributors"/>
</a>

See the full [GitHub contributors list](https://github.com/traffic-hunter/titan/graphs/contributors).

## License

MIT License. See [LICENSE](LICENSE).
