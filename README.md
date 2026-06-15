# Titan

[![Release](https://img.shields.io/github/v/release/traffic-hunter/titan)](https://github.com/traffic-hunter/titan/releases)
[![Maven Central](https://img.shields.io/maven-central/v/org.traffichunter.titan/titan-stomp)](https://central.sonatype.com/artifact/org.traffichunter.titan/titan-stomp)
[![CI](https://github.com/traffic-hunter/titan/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/traffic-hunter/titan/actions/workflows/ci.yml)

Titan is a lightweight message dispatch platform focused on STOMP over TCP.
It provides a custom NIO transport, destination routing, fanout delivery, and
Spring Boot client integration.

## Highlights

- STOMP over TCP server and client.
- Destination-based routing with `/queue/...` and `/topic/...` style paths.
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
implementation("org.traffichunter.titan:titan-spring-client:0.7.2")
```

STOMP client/server:

```kotlin
implementation("org.traffichunter.titan:titan-stomp:0.7.2")
```

Fanout support:

```kotlin
implementation("org.traffichunter.titan:titan-fanout:0.7.2")
```

Monitoring support:

```kotlin
implementation("org.traffichunter.titan:titan-monitor:0.7.2")
```

Bootstrap/runtime support:

```kotlin
implementation("org.traffichunter.titan:titan-bootstrap:0.7.2")
implementation("org.traffichunter.titan:titan-core:0.7.2")
```

## Standalone Server

Download the executable server jar from GitHub Releases.

Using `curl`:

```bash
curl -L -o titan-server-0.7.2.jar \
  https://github.com/traffic-hunter/titan/releases/download/0.7.2/titan-server-0.7.2.jar
```

Using `wget`:

```bash
wget -O titan-server-0.7.2.jar \
  https://github.com/traffic-hunter/titan/releases/download/0.7.2/titan-server-0.7.2.jar
```

Create `titan-env.yml`.

```yaml
titan:
  monitor:
    enabled: true
    host: 127.0.0.1
    port: 7777
    # token: change-me
  servers:
    - name: stomp-dispatch
      protocol: stomp
      host: 0.0.0.0
      port: 61613
      transport-options:
        reuse-address: "true"
        child-tcp-no-delay: "true"
      protocol-options:
        supported-versions: "1.2"
        max-body-length: "1048576"
        heartbeat-x: "1000"
        heartbeat-y: "1000"
        fanout-mode: "virtual"
```

Run Titan.

```bash
java -Dtitan.environment.path=./titan-env.yml -jar titan-server-0.7.2.jar
```

Inspect the running server from a terminal. The release page provides
prebuilt `titan-cli-<version>-<os>-<arch>.tar.gz` archives, or you can run the
CLI from source while developing.

```bash
# prebuilt archive example
tar -xzf titan-cli-0.7.2-linux-amd64.tar.gz
./titan --addr http://localhost:7777

# source checkout example
cd titan-cli
go run . --addr http://localhost:7777
go run . --addr http://localhost:7777 --view queues
go run . --addr http://localhost:7777 --view jvm --interval 1s --timeout 3s
go run . --addr http://localhost:7777 --no-color --once

# queue management
export TITAN_MONITOR_TOKEN=<monitor-token>
go run . --addr http://localhost:7777 queue list
go run . --addr http://localhost:7777 queue create /queue/orders --capacity 100
go run . --addr http://localhost:7777 queue delete /queue/orders
go run . --addr http://localhost:7777 queue delete /queue/orders --force
```

The monitor HTTP API is served under `/titan`.

```bash
curl http://localhost:7777/titan/monitor/health
curl http://localhost:7777/titan/monitor/snapshot
curl http://localhost:7777/titan/monitor/queues
```

## Examples

- [Spring client usage](./docs/examples/spring-client.md)
- [STOMP client usage](./docs/examples/client.md)
- [Server usage](./docs/examples/server.md)

## Architecture

![image](./docs/image/titan-archtecture.png)

1. `TitanBootstrap` reads `titan-env.yml` and builds runtime settings.
2. `TitanApplication` selects protocol/transport engines through `ServiceLoader`.
3. `FanoutLauncher` implementations attach optional fanout behavior.
4. Event loops and channel pipelines process frames and dispatch messages.

## Published Modules

- `titan-bootstrap`: loads `titan-env.yml` and starts Titan runtime modules.
- `titan-core`: provides event loop, channel, transport, and runtime primitives.
- `titan-stomp`: provides STOMP codec, server, client, and STOMP transport integration.
- `titan-fanout`: routes one published message to all matching subscribers.
- `titan-monitor`: exposes JVM and dispatcher queue monitoring snapshots.
- `titan-spring-client`: provides Spring Boot auto-configuration, `TitanTemplate`, and `@TitanListener`.

## Repository Modules

- `bootstrap`: startup, environment loading, lifecycle bootstrap.
- `core`: transport/event loop/channel/dispatcher/concurrency primitives.
- `titan-stomp`: STOMP codec, STOMP server/client transport, STOMP engine provider.
- `fanout`: fanout gateway and exporter implementations.
- `monitor`: monitoring snapshot model, JMX collectors, and Jetty HTTP endpoint.
- `titan-cli`: Go CLI for rendering monitoring snapshots in the terminal.
- `titan-spring-client`: Spring Boot auto-configuration and listener/template API.
- `smoke-test`: smoke applications and integration tests.
- `benchmark`: JMH benchmark setup.

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

- Primary production focus is STOMP over TCP.
- Titan is a networked dispatch/fanout runtime, not an in-process event bus.
- Reliability strategies such as nack/retry/error-policy in Spring listener container are still evolving.
- Monitoring currently focuses on local JVM and dispatcher queue visibility.

## License

MIT License. See [LICENSE](LICENSE).
