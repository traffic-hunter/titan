# Why Titan?

Titan is designed for the space between an in-process event bus and a full
durable broker: messages must cross the network, routing must remain explicit,
and the runtime should stay small enough to understand and operate directly.

## A good fit

Titan is a good fit when your system needs:

* Real-time delivery over STOMP and TCP
* Queue- and topic-style destination paths
* Publish-subscribe fanout
* Java or Spring Boot integration
* Local operational visibility from HTTP or a terminal
* An embeddable runtime with pluggable protocol and transport engines

Common examples include notifications, chat-style messaging, telemetry fanout,
live interaction backends, and development environments that need a compact
STOMP endpoint.

## A deliberate boundary

Titan keeps messages in memory and prioritizes live dispatch. It does not
currently position itself as a durable queue or a general-purpose broker.

Choose a durable messaging system when the primary requirement is persisted
delivery across restarts, long-term retention, replay, or mature broker-level
delivery guarantees. Choose Titan when the primary requirement is lightweight,
observable real-time dispatch.

## Design principles

1. **Destinations are the API.** Producers and consumers meet at explicit
   `/queue/...` and `/topic/...` paths.
2. **The hot path stays small.** NIO event loops, channels, and pipelines move
   frames without hiding the transport lifecycle.
3. **Optional features remain optional.** Fanout and monitoring attach as
   runtime modules instead of defining the core.
4. **Operations are part of the product.** Health and queue state are available
   through both HTTP and the Titan CLI.

Continue with the [Quickstart](quickstart.md) to run your first node.
