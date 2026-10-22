# API compatibility

Titan treats the following entry points as its 1.0 application API candidates:

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

The check is deliberately strict. Adding a public method is still an API
change, even when it is binary compatible. Make such changes intentionally and
document how users should adopt them.

## Configuration compatibility

Bytecode comparison cannot detect changes to YAML keys, default values, or
runtime behavior. Treat `titan-env.yml`, `spring.titan`, delivery semantics, and
buffer ownership rules as public contracts. Changes to those contracts need
tests, documentation, and migration notes.

## Vert.x compatibility

The Vert.x client and server adapters have been deprecated since `0.9.0`. They
remain available in the 1.0 API candidate as a migration path, but new features
target Titan's native transport. New applications should use the native client,
which is the default. Removing the Vert.x adapters after 1.0 requires a new
major version.
