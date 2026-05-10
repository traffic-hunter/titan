# Titan

Titan is a lightweight message dispatch platform focused on STOMP over TCP.
It provides a custom NIO transport, destination routing, fanout delivery, and
Spring Boot client integration.

## Highlights

- STOMP over TCP server and client.
- Destination-based routing with `/queue/...` and `/topic/...` style paths.
- Fanout delivery for publish-subscribe scenarios.
- Pluggable runtime through SPI.
- Spring Boot integration with `TitanTemplate` and `@TitanListener`.

Titan is best suited for real-time, in-memory dispatch scenarios such as
internal event buses, notifications, chat-style messaging, telemetry fanout, and
live interaction backends. It is not currently positioned as a durable
queue/broker replacement.

## Installation

Titan artifacts are published to Maven Central.

```kotlin
repositories {
    mavenCentral()
}
```

Spring client:

```kotlin
implementation("org.traffichunter.titan:titan-spring-client:0.5.2")
```

STOMP client/server:

```kotlin
implementation("org.traffichunter.titan:titan-stomp:0.5.2")
```

Fanout support:

```kotlin
implementation("org.traffichunter.titan:titan-fanout:0.5.2")
```

Bootstrap/runtime support:

```kotlin
implementation("org.traffichunter.titan:titan-bootstrap:0.5.2")
implementation("org.traffichunter.titan:titan-core:0.5.2")
```

## Standalone Server

Download the executable server jar from GitHub Releases.

Using `curl`:

```bash
curl -L -o titan-server-0.5.2.jar \
  https://github.com/traffic-hunter/titan/releases/download/0.5.2/titan-server-0.5.2.jar
```

Using `wget`:

```bash
wget -O titan-server-0.5.2.jar \
  https://github.com/traffic-hunter/titan/releases/download/0.5.2/titan-server-0.5.2.jar
```

Create `titan-env.yml`.

```yaml
titan:
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
java -Dtitan.environment.path=./titan-env.yml -jar titan-server-0.5.2.jar
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
- `titan-spring-client`: provides Spring Boot auto-configuration, `TitanTemplate`, and `@TitanListener`.

## Repository Modules

- `bootstrap`: startup, environment loading, lifecycle bootstrap.
- `core`: transport/event loop/channel/dispatcher/concurrency primitives.
- `titan-stomp`: STOMP codec, STOMP server/client transport, STOMP engine provider.
- `fanout`: fanout gateway and exporter implementations.
- `monitor`: experimental monitoring module, not ready for general use.
- `titan-spring-client`: Spring Boot auto-configuration and listener/template API.
- `smoke-test`: smoke applications and integration tests.
- `benchmark`: JMH benchmark setup.

## Development

Requirements:

- JDK 21+
- Gradle wrapper (`./gradlew`)

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
- Reliability strategies such as nack/retry/error-policy in Spring listener container are still evolving.
- Monitoring is still under development and not ready for general use.

## License

MIT License. See [LICENSE](LICENSE).
