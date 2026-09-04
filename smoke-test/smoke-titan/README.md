# Native Client Smoke Tests

These tests build the standalone `titan-server` JAR and run it in a separate JVM
on a loopback port. This exercises configuration loading, SPI assembly, the
dispatch runtime, and process shutdown in the same form users run. Tests use
`TitanClient` for normal traffic and a small TCP peer for incomplete frames and
a subscriber that stops reading. Docker is not required.

## Run

From the repository root:

```bash
./gradlew :smoke-test:smoke-titan:test --rerun-tasks
./gradlew :smoke-test:smoke-spring:test
```

Run one group with `--tests '*TitanMessagingSmokeTest'`,
`--tests '*TitanConnectionIsolationSmokeTest'`, or
`--tests '*TitanLifecycleSmokeTest'`.

The HTML report is in `build/reports/tests/test/index.html` under this module.

## Scenarios

| Group | Checks |
| --- | --- |
| Messaging | Concurrent producers and two subscribers; empty, UTF-8, and 48 KiB payloads; line breaks and embedded NUL with explicit content-length; subscription restore after server restart |
| Connection isolation | Split and coalesced SEND frames; oversized input without a terminator; disconnect during a partial frame; a non-reading subscriber alongside a healthy subscriber |
| Lifecycle | Explicit disconnect without reconnect; outstanding sends at shutdown; rejection and direct-buffer release after shutdown; packaged process termination |

Messaging and client lifecycle scenarios cover TCP and WebSocket. Socket fault
injection uses TCP. The slow-consumer scenario runs three times and verifies
that a non-reading subscriber does not delay a healthy subscriber.

Subscription futures wait for STOMP receipts before tests publish. Tests do not
retry failed sends to turn a failed delivery into a passing assertion.
The shutdown scenario permits either success or failure for outstanding sends,
but requires every future to finish.

Each class runs in a fresh test JVM, and every invocation owns a separate Titan
process. JUnit timeouts run on a separate thread so a stalled client cannot block
the runner itself. Thread dumps are enabled on timeout, and failed tests attach
the standalone process log to the JUnit report.

## Regression Findings

Observed on 2026-09-03, JDK 21.0.11, local checkout based on `a6279425`:

- An oversized unterminated SEND closed the connection without delivering an
  ERROR frame. The decoder's size exception reached the channel read failure
  path. STOMP 1.2 recommends ERROR followed by close for size-limit violations.
- Valid multiline and embedded-NUL payloads were rejected on both TCP and
  WebSocket. The parser searches for NUL before interpreting content-length and
  takes the last decoded line as the body. Neither operation preserves an
  arbitrary message body.
- Waiting for server event loops to terminate after server shutdown failed.
  Explicit fixture cleanup must not count as successful transport shutdown.
- Shutdown stalled during a queued-send run and again during concurrent fanout
  on a later run. One stack stopped in `FanoutDispatchChainHandler.close()` at
  `ConcurrentHashMap.clear()`; another waited for the dispatch executor to
  terminate. The thread
  dump also showed a dispatch virtual thread waiting for the Trie write lock
  inside `consumers.computeIfAbsent()`, with other virtual threads waiting for
  that map entry. A stalled scenario can affect later scenarios in the same
  class. The exact locking/scheduling cause still needs investigation.

The fixes following that run changed frame parsing to honor content-length,
send an ERROR before closing an oversized server connection, always request
shutdown for transport-owned event loops, and register fanout consumers before
starting their tasks. The complete native smoke suite subsequently passed twice
in succession.

The regression assertions remain enabled. These tests detect defects; they do
not establish maximum throughput, absence of all buffer leaks, or long-running
stability. TLS and cross-machine network failures are outside this set.

Protocol reference: [STOMP 1.2](https://stomp.github.io/stomp-specification-1.2.html),
especially content-length, frame bodies, and size limits.
