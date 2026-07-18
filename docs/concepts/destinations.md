# Destinations

A destination is the address shared by a producer and its consumers. Titan uses
STOMP-style paths so routing intent is visible at every call site.

## Queue-style paths

Use `/queue/...` when the destination represents work or a named dispatch lane.

```text
Producer ── SEND /queue/orders ──▶ Titan ──▶ Consumer
```

Examples:

* `/queue/orders`
* `/queue/image-resize`
* `/queue/tenant-42/events`

## Topic-style paths

Use `/topic/...` when a publication represents a live event that subscribers
observe.

```text
                              ┌──▶ Subscriber A
Producer ── /topic/news ──▶ Titan
                              └──▶ Subscriber B
```

Examples:

* `/topic/notifications`
* `/topic/room/42`
* `/topic/device/temperature`

Topic-style naming communicates intent, while actual one-to-many delivery is
provided by Titan's [fanout](fanout.md) module.

## Naming destinations

Prefer stable nouns and place variable identifiers after the domain:

```text
/queue/orders
/topic/orders/created
/topic/rooms/{roomId}/messages
```

Avoid placing deployment details, hostnames, or consumer implementation names
in a destination. Those details change more frequently than the message's
meaning.
