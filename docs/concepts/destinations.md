# Dispatch routing

Titan's fanout runtime routes messages by **exact destination identity**. A
destination is a validated path stored in `Destination`; it is not classified
as a queue or topic from its prefix.

```text
/orders
/orders/created
/events/notifications
```

All three are ordinary routing keys. `/queue/orders` and `/topic/orders` are
also valid strings, but `queue` and `topic` have no reserved meaning inside the
dispatcher.

## The publish path

With `fanout-mode` enabled, Titan replaces the STOMP server's default `SEND`
handler with `StompSendToFanoutHandler`. One inbound frame then follows this
path:

```mermaid
sequenceDiagram
    participant P as STOMP producer
    participant H as StompSendToFanoutHandler
    participant G as DispatchGateway
    participant D as Dispatcher
    participant Q as DispatcherQueue
    participant C as Destination consumer
    participant E as StompDispatchExporter

    P->>H: SEND destination=/orders
    H->>G: publish(Message{/orders})
    G->>D: getOrPut(/orders)
    D-->>G: exact queue for /orders
    G->>Q: enqueue(message)
    G->>C: ensure one consumer for /orders
    C->>Q: dispatch with timeout
    Q-->>C: next FIFO message
    C->>E: export(/orders, payload)
    E->>E: find subscriptions equal to /orders
```

The implementation divides this into two handler-chain stages:

1. `RouteDispatchChainHandler` invokes `DispatchGateway.route(message)`.
2. `FanoutDispatchChainHandler` ensures that the destination consumer is
   running.

Custom handlers are inserted between those stages. This allows backup,
validation, or metrics work after routing and before consumer startup without
putting protocol logic inside the queue.

## How a queue is selected

`DispatchGateway.route` does not search for the nearest path, a topic pattern,
or every matching prefix. It performs this operation:

```java
Destination destination = message.getDestination();
DispatcherQueue queue = dispatcher.getOrPut(destination);
queue.enqueue(message);
```

Consequently:

| Published destination | Selected dispatcher queue |
| --- | --- |
| `/orders` | `/orders` |
| `/orders/created` | `/orders/created` |
| `/orders/cancelled` | `/orders/cancelled` |

Even though these keys share a trie prefix, they are three independent queues.
Publishing to `/orders/created` never falls back to the `/orders` queue.

{% hint style="warning" icon="asterisk" %}
`Dispatcher.searchAll("/orders/*")` is a queue discovery operation that returns
descendant queues. The publish path never calls `searchAll`; wildcard lookup is
not message routing. Do not use a wildcard destination in `SEND` expecting
broadcast behavior.
{% endhint %}

## Queue creation and capacity

`getOrPut` atomically returns the existing queue or creates one for the exact
destination. A queue created by normal publishing uses
`DispatcherQueue.DEFAULT_CAPACITY`, currently `11` messages.

You can create the queue before traffic arrives and choose its capacity:

```bash
titan --addr http://localhost:7777 queue create /orders --max-pending-bytes 1048576
```

Queue creation is idempotent. If `/orders` already exists, a later create call
returns that queue and does not replace its original capacity.

The queue is a bounded FIFO `LinkedBlockingQueue` and is the handoff point
between producers and the destination consumer:

* enqueue preserves insertion order;
* pause blocks new enqueue attempts until resume;
* size and capacity are exposed through monitoring and JMX;
* non-empty deletion is rejected unless force deletion is requested;
* force deletion clears pending messages and stops the current consumer;
* publishing after deletion creates a new queue instance.

`enqueue` can refuse a message when a bounded queue is full. Titan's current
fanout path does not provide durable retry or persistence for that refusal, so
capacity and queue pressure must be monitored.

## One consumer per destination

`DispatchGateway` keeps a concurrent map of group and destination to consumer
task. `computeIfAbsent` guarantees at most one active queue-draining task for
each destination within a group inside that gateway. The same destination in
two groups is two queues and two consumers.

The consumer polls its queue and invokes the configured `DispatchExporter` one
message at a time. This preserves FIFO processing within one destination while
allowing different destination consumers to progress independently on the
gateway executor.

The `publish()` future represents completion of the dispatch handler chain and
consumer-start request. It does **not** mean that every remote subscriber has
received or acknowledged the message.

## How subscriptions match

After a message leaves the queue, `StompDispatchExporter` asks the server's
subscription registry for subscriptions whose `Destination` is exactly equal
to the message destination.

```text
Message destination       Subscription destination       Result
/orders                    /orders                        match
/orders/created            /orders                        no match
/orders                    /orders/*                      no match
```

For every exact match, the exporter creates a separate STOMP `MESSAGE` frame
and copies the subscription id into its headers. This is the fanout step: one
message drained from one dispatcher queue can be written to multiple matching
subscriptions.

If there are no exact-match subscriptions, the exporter has no recipients. The
message has already been removed from the in-memory queue; Titan does not retain
it for a future subscriber.

## Destination groups

A group is a namespace for destinations. `/orders` in group `market` and
`/orders` in group `notification` are two independent queues with independent
subscribers. Traffic that names no group belongs to the `default` group, which
always exists.

Producers and subscribers pick a group per frame with the `group` header:

```text
SEND
destination:/orders
group:market

{"id":42}^@
```

```text
SUBSCRIBE
destination:/orders
id:sub-1
group:market

^@
```

Rules:

- The header is optional on `SEND` and `SUBSCRIBE`. Without it the frame is in
  the `default` group. An empty value is treated the same as no header.
- A group is created the first time a `SEND` names it. Subscribing alone does
  not create a group or a queue, the same way subscribing does not create a
  destination queue.
- Names match `^[a-zA-Z0-9_-]{1,64}$`. A malformed name is answered with an
  `ERROR` frame and the connection is closed.
- `MESSAGE` frames carry `group` only when the message came from a group other
  than `default`. A client that never sends the header never receives it.
- Subscriptions match on group **and** exact destination. A `market` subscriber
  of `/orders` never receives a `default` or `notification` message for
  `/orders`.
- The deprecated Vert.x STOMP transport does not support groups. A `SEND` with
  a `group` header on that transport is refused with an `ERROR` frame.

The Java client and the Spring integration take the group as the first argument
of their send and subscribe methods. See
[the client example](../examples/client.md#destination-groups) and
[the Spring example](../examples/spring-client.md#destination-groups).

Queue management over HTTP and the CLI still operates on the `default` group.

## Fanout mode versus the default STOMP handler

The dispatcher-queue path described above is installed when `fanout-mode` is
configured. Without that adapter, Titan's default STOMP `SEND` handler looks up
exact-match subscriptions and writes to them directly; it does not pass the
frame through `DispatchGateway` or a `DispatcherQueue`.

For the standalone configuration documented here, enable fanout explicitly:

```yaml
titan:
  servers:
    - name: stomp-dispatch
      protocol: stomp
      protocol-options:
        fanout-mode: "virtual"
```

This distinction matters when embedding the STOMP server: installing
`StompSendToFanoutHandler` is what connects inbound `SEND` frames to Titan's
dispatcher routing pipeline.
