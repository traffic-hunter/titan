# Modules

Titan is published as focused Maven artifacts so applications can depend on the
runtime layers they actually use.

| Artifact | Responsibility |
| --- | --- |
| `titan-bootstrap` | Environment loading and runtime startup |
| `titan-core` | Event loops, channels, TCP/WebSocket/TLS transport, dispatch, and concurrency primitives |
| `titan-stomp` | STOMP codec, TCP/WebSocket server, clients, and engine integration |
| `titan-fanout` | One-to-many dispatch gateways and exporters |
| `titan-monitor` | JVM and dispatcher monitoring snapshots and HTTP endpoints |
| `titan-spring-client` | Spring Boot auto-configuration, `TitanTemplate`, and `@TitanListener` |

## Maven coordinates

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("org.traffichunter.titan:titan-stomp:0.7.4")
}
```

Replace the artifact name with the module required by your application. A
standalone distribution typically combines bootstrap, core, STOMP, and the
optional fanout and monitoring modules.
