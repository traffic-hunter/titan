# Why Titan?

Titan is designed for the space between an in-process event bus and a full
durable broker: messages must cross the network, routing must remain explicit,
and the runtime should stay small enough to understand and operate directly.

## A good fit

Titan is a good fit when your system needs:

* Real-time delivery over STOMP using TCP or WebSocket
* Exact destination matching backed by a FIFO dispatcher queue
* Publish-subscribe fanout
* Java or Spring Boot integration
* One `TitanClient` API for the native and Vert.x client implementations
* Reconnection with active subscription recovery
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

1. **Destinations are the API.** Every destination is an opaque path key. Titan
   maps it to one FIFO dispatcher queue; prefixes such as `/queue` and `/topic`
   do not select different delivery semantics.
2. **The hot path stays small.** NIO event loops, channels, and pipelines move
   frames without hiding the transport lifecycle.
3. **Public APIs hide transport details.** Applications use `TitanClient` and
   transport-neutral STOMP frames while native and Vert.x drivers remain behind
   the facade.
4. **Optional features remain optional.** Dispatch and monitoring attach as
   runtime modules instead of defining the core.
5. **Operations are part of the product.** Health and queue state are available
   through both HTTP and the Titan CLI.

Continue with the [Quickstart](quickstart.md) to run your first node.
