# How Titan works

Titan turns a network frame into a routed message through a short, explicit
pipeline.

```text
Client
  │  STOMP frame
  ▼
Transport → Channel pipeline → Destination routing → Subscriber
     │                                      │
     └── Event loops                        └── Optional fanout
```

## Runtime startup

1. `TitanBootstrap` reads `titan-env.yml`.
2. `TitanApplication` discovers protocol and transport engines with
   `ServiceLoader`.
3. Each configured server binds its network transport.
4. Optional launchers attach dispatch and monitoring behavior.

This separation lets the bootstrap layer assemble a runtime without coupling
the core event loop and channel primitives to a single protocol.

## Client facade

Applications connect through `TitanClient`. The facade owns lifecycle,
connection recovery, and logical subscriptions while a hidden driver performs
native Titan or Vert.x transport work. This keeps application code independent
from the selected networking implementation and allows active subscriptions to
be restored after an unexpected disconnect.

## Message path

After a client connects, Titan decodes STOMP frames in a channel pipeline. A
`SEND` frame carries a destination. Routing selects the matching subscription
or dispatch path, and the resulting `MESSAGE` frame is written to the target
connection.

Heartbeats belong to the connection lifecycle rather than the application
payload. The server and client negotiate them during STOMP connection setup.

## Runtime views

Titan exposes the same system from three useful angles:

* **Protocol:** STOMP commands, headers, subscriptions, and acknowledgements
* **Runtime:** event loops, channels, dispatchers, and promises
* **Operations:** JVM, connection, server, and queue snapshots

Next, learn how [destinations](destinations.md) define routing intent.
