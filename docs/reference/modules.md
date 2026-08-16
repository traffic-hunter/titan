# Modules

Titan is published as focused Maven artifacts so applications can depend on the
runtime layers they actually use.

| Artifact | Responsibility |
| --- | --- |
| `titan-bootstrap` | Environment loading and runtime startup |
| `titan-core` | Event loops, channels, TCP/WebSocket/TLS transport, and concurrency primitives |
| `titan-stomp` | STOMP codec, TCP/WebSocket server, and low-level client drivers |
| `titan-client` | Transport-neutral `TitanClient` facade for native and Vert.x clients |
| `titan-dispatch` | Routing, queue management, and one-to-many dispatch gateways and exporters |
| `titan-monitor` | JVM and dispatcher monitoring snapshots and HTTP endpoints |
| `titan-spring-client` | Spring Boot auto-configuration, `TitanTemplate`, and `@TitanListener` |

## Maven coordinates

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("org.traffichunter.titan:titan-client:0.8.0")
}
```

Applications should normally depend on `titan-client` or
`titan-spring-client`. Use `titan-stomp` when embedding a server or working with
the low-level protocol surface. The standalone distribution combines bootstrap,
core, STOMP, dispatch, and monitoring modules.
