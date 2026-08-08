<p align="center">
  <img src=".github/assets/titan-logo-transparent.png" alt="Titan" width="160">
</p>

# Titan

[![Release](https://img.shields.io/github/v/release/traffic-hunter/titan)](https://github.com/traffic-hunter/titan/releases)
[![Maven Central](https://img.shields.io/maven-central/v/org.traffichunter.titan/titan-stomp)](https://central.sonatype.com/artifact/org.traffichunter.titan/titan-stomp)
[![CI](https://github.com/traffic-hunter/titan/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/traffic-hunter/titan/actions/workflows/ci.yml)

Titan is a lightweight message dispatch platform focused on STOMP over TCP and
WebSocket.
It provides a custom NIO transport, destination routing, fanout delivery, and
Spring Boot client integration.

## Highlights

- STOMP over TCP server and client.
- STOMP over WebSocket for native, Vert.x, and Spring clients.
- Transport-neutral `TitanClient` facade with reconnect and subscription recovery.
- TLS transport support through PKCS12 or JKS key stores.
- Exact destination matching with one FIFO dispatcher queue per destination.
- Fanout delivery for publish-subscribe scenarios.
- Pluggable runtime through SPI.
- Local HTTP monitoring API with a terminal-first CLI.
- Spring Boot integration with `TitanTemplate` and `@TitanListener`.

Titan is best suited for real-time, in-memory dispatch scenarios such as
notifications, chat-style messaging, telemetry fanout, and live interaction
backends. It is not an in-process event bus and is not currently positioned as a
durable queue/broker replacement.

## Installation

Titan artifacts are published to Maven Central.

```kotlin
repositories {
    mavenCentral()
}
```

Spring client:

```kotlin
implementation("org.traffichunter.titan:titan-spring-client:0.7.4")
```

Standalone Java client:

```kotlin
implementation("org.traffichunter.titan:titan-client:0.7.4")
```

STOMP server and low-level transport APIs:

```kotlin
implementation("org.traffichunter.titan:titan-stomp:0.7.4")
```

Fanout support:

```kotlin
implementation("org.traffichunter.titan:titan-dispatch:0.7.4")
```

Monitoring support:

```kotlin
implementation("org.traffichunter.titan:titan-monitor:0.7.4")
```

Bootstrap/runtime support:

```kotlin
implementation("org.traffichunter.titan:titan-bootstrap:0.7.4")
implementation("org.traffichunter.titan:titan-core:0.7.4")
```

The native implementation is selected by default. Use
`implementation(TitanClient.Implementation.VERTX)` to select Vert.x without
changing the messaging API. After an unexpected connection loss, the client
uses its configured reconnect policy and restores active subscriptions before
reporting itself as connected again.

## Examples

- [Spring client usage](./docs/examples/spring-client.md)
- [Java client usage](./docs/examples/client.md)
- [Server usage](./docs/examples/server.md)

## Development

Requirements:

- JDK 21+
- Gradle wrapper (`./gradlew`)
- Go 1.22+ for `titan-cli`

Run tests:

```bash
./gradlew test
```

Run smoke tests:

```bash
./gradlew :smoke-test:smoke-spring:test
./gradlew :smoke-test:smoke-titan:test
```

Build the standalone server jar:

```bash
./gradlew :bootstrap:shadowJar
```

## Scope

- Primary production focus is STOMP over TCP and WebSocket.
- Titan is a networked dispatch/fanout runtime, not an in-process event bus.
- Reliability strategies such as nack/retry/error-policy in Spring listener container are still evolving.
- Monitoring currently focuses on local JVM and dispatcher queue visibility.

## Contributors

Thanks to everyone who has contributed to Titan.

<a href="https://github.com/traffic-hunter/titan/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=traffic-hunter/titan" alt="Titan contributors"/>
</a>

See the full [GitHub contributors list](https://github.com/traffic-hunter/titan/graphs/contributors).

## License

MIT License. See [LICENSE](LICENSE).
