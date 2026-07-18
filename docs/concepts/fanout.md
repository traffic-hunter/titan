# Fanout

Fanout takes one publication and dispatches it to every matching subscriber.
It is useful for notifications, live rooms, and telemetry streams where each
active observer should receive the event.

```text
                           ┌──▶ subscription 1
SEND /topic/updates ──▶ gateway ──▶ subscription 2
                           └──▶ subscription 3
```

## Enable fanout

Set `fanout-mode` in the server's protocol options:

```yaml
titan:
  servers:
    - name: stomp-dispatch
      protocol: stomp
      protocol-options:
        fanout-mode: "virtual"
```

The `titan-fanout` module supplies the gateway and exporter that connect STOMP
`SEND` frames to matching subscriptions. The `virtual` mode uses virtual-thread
based dispatch workers.

## Operational boundary

Fanout is live delivery, not durable retention. A subscriber that is offline
does not gain replay merely because the destination is a topic. If consumers
must recover historical events after reconnecting, provide persistence outside
Titan or choose a durable messaging system.
