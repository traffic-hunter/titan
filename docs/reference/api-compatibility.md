# API compatibility

Titan separates application entry points from implementation types that happen
to be public for reflection or communication between modules. Only documented
entry points become part of the stable 1.0 contract.

## 1.0 API inventory

The following application APIs are being reviewed for 1.0:

| Area | Candidate API |
| --- | --- |
| Java client | `TitanClient`, `TitanClient.Builder`, `StompEndpoint`, `ClientException` |
| Client values | `Buffer`, `Handler`, `StompFrames`, `StompHeaders.Elements` |
| Client options | `StompSessionOption`, `InetClientOption`, `RetryPolicy`, `RetryListener`, `TlsOptions`, `TlsSide`, `TlsVersion`, `TlsClientAuth` |
| Spring | `TitanTemplate`, `StompOperations`, `@EnableTitan`, `@TitanListener`, `TitanProperties` |
| Server startup | `TitanBootstrap.run(String)` |
| Configuration | documented keys and defaults under `titan-env.yml` and `spring.titan` |
| Operations | documented monitor HTTP endpoints and CLI commands |

The client implementation, transport drivers, physical connection adapters,
workers, and subscription registry are package-private. They can change without
adding transport-specific branches to application code.

Server embedding and dispatch extension are not part of the stable API.
`EventLoopGroups`, `StompServer`, `StompServerOption`, `DispatchGateway`, the
STOMP handlers, and the engine SPI can change in any release, including 1.x
releases. The supported way to run a server is `titan-env.yml`, started through
`TitanBootstrap.run(String)`, the standalone JAR, or the container image.

Some implementation types must remain public for YAML binding, service loading,
or module boundaries. Examples include mutable YAML property objects, transport
providers, channel implementations, servlet classes, and JMX collectors. Public
visibility alone does not make them stable application APIs.

## Compatibility check

The build compares every Maven-published module with Titan `0.9.0` by default.
It checks public and protected class, method, field, constructor, and interface
signatures.

```bash
./gradlew checkApiCompatibility
```

Use a different released baseline when investigating an older version:

```bash
./gradlew checkApiCompatibility -PapiBaselineVersion=0.8.4
```

Reports are written to `<module>/build/reports/japicmp/`. CI runs the same check
and uploads these reports when a comparison fails.

Titan does not guarantee compatibility between `0.x` releases. Before 1.0, the
task records API changes but does not fail the build. This keeps the remaining
API cleanup visible without preserving an interface that has not reached a
stable release.

Strict mode is available for investigation before a stable baseline is accepted:

```bash
./gradlew checkApiCompatibility -PapiCompatibilityStrict=true
```

Strict mode examines every public type in the published modules, including
implementation types outside the inventory, so it is an investigation aid rather
than a gate. Read its report against the inventory above: a change to an
inventory type is an API change, even when it only adds a binary-compatible
method, while a change to any other type is not.

## Configuration compatibility

Bytecode comparison cannot detect changes to YAML keys, default values, or
runtime behavior. Treat `titan-env.yml`, `spring.titan`, delivery semantics, and
buffer ownership rules as public contracts. Before 1.0, changes need tests,
documentation, and release notes. Compatibility guidance becomes mandatory
after the stable contract has been published.

## Transport compatibility

Titan 1.0 exposes its native TCP, WebSocket, and TLS transports. Transport
implementation details remain outside the supported application API; clients
configure them through `TitanClient.Builder` and Spring properties.
