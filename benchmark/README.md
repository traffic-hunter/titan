# Titan Benchmarks

Titan keeps microbenchmarks and end-to-end performance tests separate because they answer
different questions.

- `benchmark:jmh` measures isolated in-process components.
- `benchmark:perf-test` uses the Java `TitanClient` to drive load against a running server.
- `titan perf-test` collects settings, launches that Java runner, and renders its result.

## JMH

JMH measures individual in-process components such as dispatchers and queues.

```bash
./gradlew :benchmark:jmh:jmh
```

## Performance test

The performance test connects real Titan clients to a running Titan server. It reports message
throughput and end-to-end p50, p95, and p99 latency.

Build the Titan CLI:

```bash
./gradlew :benchmark:perf-test:shadowJar
cd titan-cli
go build -o titan .
```

Run an end-to-end performance test:

```bash
./titan perf-test --host 127.0.0.1 --port 7777 --messages 10000 --producers 4 --payload-bytes 1024
```

Use `./titan perf-test --help` to list every option. End-to-end performance tests are not part of the
regular Gradle build lifecycle.

The CLI finds the runner under `benchmark/perf-test/build/libs` in a source checkout. A packaged
CLI can place it at `lib/titan-perf-runner.jar` next to the `titan` executable or set the
`TITAN_PERF_RUNNER` environment variable.
