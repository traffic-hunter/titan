# API compatibility

The following entry points are being reviewed for Titan's 1.0 application API:

- `TitanClient` and `TitanClient.Builder`
- Spring Boot configuration under `spring.titan`
- `TitanTemplate` and `@TitanListener`
- `DispatchGateway` and its handler-chain extension point
- server settings documented in `titan-env.yml`

The lower-level modules remain public because Titan can also be embedded, but
applications should normally start with `titan-client` or
`titan-spring-client`. Types in `titan-core` and `titan-stomp` expose transport
and protocol details and require more care when upgrading.

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

Strict mode is available once a stable baseline has been accepted:

```bash
./gradlew checkApiCompatibility -PapiCompatibilityStrict=true
```

In strict mode, adding a public method still counts as an API change even when
it is binary compatible.

## Configuration compatibility

Bytecode comparison cannot detect changes to YAML keys, default values, or
runtime behavior. Treat `titan-env.yml`, `spring.titan`, delivery semantics, and
buffer ownership rules as public contracts. Changes to those contracts need
tests, documentation, and migration notes.

## Vert.x compatibility

The Vert.x client and server adapters have been deprecated since `0.9.0`. New
features target Titan's native transport, which is the default. Whether the
adapters remain in 1.0 must be decided before the first release candidate. If
they are included in 1.0, removing them later requires a new major version.
