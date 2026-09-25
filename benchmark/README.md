# Titan Benchmarks

Titan keeps microbenchmarks and end-to-end performance tests separate because they answer
different questions.

- `benchmark:jmh` measures isolated in-process components.
- `benchmark:perf-test` uses the Java `TitanClient` to drive load against a running server.
- `benchmark:stability-fixture` starts a broker whose delivery path, transport, and queue limits
  are all named on the command line.
- `titan perf-test` collects settings, launches that Java runner, and renders its result.

## JMH

JMH measures individual in-process components such as dispatchers and queues.

```bash
./gradlew :benchmark:jmh:jmh
```

## Performance test

The performance test connects real Titan clients to a running Titan server. It reports every stage
of a message separately: how many sends were attempted, how many the broker accepted, how many
arrived, and how many ended in a result nobody can explain.

A stage the runner cannot observe is reported as `not measured`, never as a zero. `written` is
always one of those: a channel completes a write once the outbound pipeline has run, which can
still leave bytes in the channel's own buffer, so no run here can claim a socket write was seen
through.

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

### Send modes

`--send-mode receipt` is the default. Each SEND carries a receipt, so a completed send means the
broker accepted that message. An accepted identifier that has not arrived is reported separately.
The current runner cannot correlate every broker ERROR with a request; those failures remain unknown.

`--send-mode write` completes a send as soon as the local write is submitted. It says nothing about
the broker and exists only to compare publish rates; a write-mode run that receives everything is
not evidence that anything was accepted. Waiting for receipts also paces a producer to one
outstanding message, so the two modes publish at very different rates by design.

Use `./titan perf-test --help` to list every option. End-to-end performance tests are not part of the
regular Gradle build lifecycle.

The CLI finds the runner under `benchmark/perf-test/build/libs` in a source checkout. A packaged
CLI can place it at `lib/titan-perf-runner.jar` next to the `titan` executable or set the
`TITAN_PERF_RUNNER` environment variable.

## Stability fixture

The fixture is a broker for measured runs. Every choice a result depends on is given on the command
line instead of read from a configuration file, and the queue limits are applied unconditionally:
`titan-env.yml` gates them behind a flow-control switch that ships disabled, so a limit printed in a
file is no evidence that any limit was in force.

```bash
./gradlew :benchmark:stability-fixture:shadowJar
java -jar benchmark/stability-fixture/build/libs/titan-stability-fixture-*.jar \
    --path dispatch --transport tcp --manifest /tmp/fixture.json
```

It prints one `TITAN_STABILITY_FIXTURE={...}` line holding the port it bound and the settings it
applied, writes the same JSON to `--manifest`, and serves until it is stopped. `--port 0`, the
default, lets the OS pick a free port so runs do not inherit a queue from each other.

Point a run at it and keep the evidence together:

```bash
./titan perf-test --port <port> --transport tcp --send-mode receipt --path-label dispatch \
    --messages 100000 --producers 4 --payload-bytes 1024 \
    --results-dir build/stability --run-id tcp-receipt-100k-p4 --iteration 1 \
    --fixture-manifest /tmp/fixture.json
```

`--results-dir` keeps the raw runner output and a manifest of the run — the options, the commit,
the fixture's own description of itself, and the report — under `<results-dir>/<run-id>/`. The raw
output is kept even when the run fails, because a run that died halfway is a result too.

`--path-label` only labels the result. The runner cannot tell the two server paths apart over
STOMP, so it must be set to whatever the fixture was started with; a direct run filed as a dispatch
run reads as a guarantee the direct path never made.
