# Fanout

Fanout takes one publication and dispatches it to every matching subscriber.
It is useful for notifications, live rooms, and telemetry streams where each
active observer should receive the event.

```text
                           ┌──▶ subscription 1
SEND /events/updates ──▶ gateway ──▶ subscription 2
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

The `titan-dispatch` module connects STOMP `SEND` frames to matching
subscriptions. The gateway puts each message into its destination queue on the
connection's event loop. One consumer per queue runs on a virtual thread and
hands the messages to the exporter, which writes them to the matching
subscriptions. `virtual` is the only mode.

## Operational boundary

Fanout is live delivery, not durable retention. A subscriber that is offline
does not gain replay from its destination name. If consumers must recover
historical events after reconnecting, provide persistence outside Titan or
choose a durable messaging system. Titan's at-most-once attempt model and the
meaning of SEND receipts are detailed in [Delivery guarantees](delivery-guarantees.md).
