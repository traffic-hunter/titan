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
| Client options | `StompSessionOption`, `InetClientOption`, `RetryPolicy`, `RetryListener`, `TlsContext` |
| Spring | `TitanTemplate`, `StompOperations`, `@EnableTitan`, `@TitanListener`, `TitanProperties` |
| Configuration | documented keys and defaults under `titan-env.yml` and `spring.titan` |
| Operations | documented monitor HTTP endpoints and CLI commands |

The client implementation, transport drivers, physical connection adapters,
workers, and subscription registry are package-private. They can change without
adding transport-specific branches to application code.

The embedded server and dispatch extension APIs still require a decision. The
current server example uses `EventLoopGroups`, `StompServer`,
`StompServerOption`, `DispatchGateway`, and its STOMP handlers directly. These
types must either become supported extension APIs before RC.1 or move to an
explicitly unstable server-embedding guide.

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

The current strict mode examines every public type in the published modules.
Before RC.1, the inventory above must be translated into explicit checker
filters so implementation types do not become stable by accident. Once that is
done, adding a public method to the supported surface still counts as an API
change even when it is binary compatible.

## Configuration compatibility

Bytecode comparison cannot detect changes to YAML keys, default values, or
runtime behavior. Treat `titan-env.yml`, `spring.titan`, delivery semantics, and
buffer ownership rules as public contracts. Before 1.0, changes need tests,
documentation, and release notes. Compatibility guidance becomes mandatory
after the stable contract has been published.

## Vert.x compatibility

The Vert.x client and server adapters have been deprecated since `0.9.0`. New
features target Titan's native transport, which is the default. Whether the
adapters remain in 1.0 must be decided before the first release candidate. If
they are included in 1.0, removing them later requires a new major version.
