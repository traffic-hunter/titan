# Roadmap

Titan's near-term direction is to make real-time, in-memory message dispatch
more predictable to operate and easier to validate. This roadmap describes
areas of work rather than release promises, and priorities may change as the
project learns from practical use.

## Reliability

- Harden client reconnect and subscription restoration behavior.
- Expand transport, WebSocket, TLS, lifecycle, and failure-path testing.
- Clarify buffer ownership and continue checking network paths for leaks.
- Improve queue and channel flow control under sustained load.

## Dispatch

- Complete destination group lifecycle and isolation semantics.
- Keep queue management behavior consistent across the Java API, monitor API,
  and CLI.
- Define delivery guarantees and failure behavior before adding stronger
  acknowledgement modes.

## Operations

- Improve health, queue, JVM, and channel visibility.
- Build repeatable performance and long-running stability tests around the
  public client API.
- Strengthen container defaults and document practical deployment patterns.
- Evaluate OpenTelemetry integration after the core runtime metrics settle.

## Toward 1.0

Before a stable release, Titan should have a documented compatibility policy,
a repeatable release checklist, stable public client and configuration APIs,
and evidence from realistic load and recovery tests.

Durable storage, clustering, and replicated delivery are not commitments in
this roadmap. Proposals for these areas should begin with a design discussion
and a clear account of their effect on Titan's lightweight runtime model.

Suggestions are welcome through
[GitHub Issues](https://github.com/traffic-hunter/titan/issues).
