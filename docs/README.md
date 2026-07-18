# Messages in motion

Titan is a lightweight message dispatch platform for real-time applications,
built around **STOMP over TCP**, destination routing, and observable in-memory
delivery.

```text
CONNECT  ──  SEND  ──  ROUTE  ──  DELIVER
                         │
                         └──  OBSERVE
```

Use Titan when notifications, live interactions, telemetry, or chat-style
traffic needs a small networked runtime without the operational weight of a
durable message broker.

## Start here

| I want to… | Go to |
| --- | --- |
| See a message move through Titan | [Quickstart](quickstart.md) |
| Understand where Titan fits | [Why Titan?](why-titan.md) |
| Run a standalone server | [Run a server](examples/server.md) |
| Connect a Java application | [Use the STOMP client](examples/client.md) |
| Connect a Spring Boot application | [Integrate Spring Boot](examples/spring-client.md) |
| Inspect a running node | [Monitoring and CLI](operate/monitoring.md) |

## One runtime, three views

### Build

Start a server from `titan-env.yml`, embed the STOMP server API, or connect with
the native Java and Spring Boot clients.

### Route

Address messages with familiar destinations such as `/queue/orders` and
`/topic/notifications`. Optional fanout delivers a publication to matching
subscribers.

### Observe

Inspect health, JVM state, connections, and dispatcher queues through the local
monitoring API or the terminal-first Titan CLI.

> Titan focuses on fast, real-time, in-memory dispatch. It is not currently a
> durable queue or broker replacement. See [Project scope](reference/project-scope.md)
> before choosing it for reliability-sensitive workloads.

## Current release

The examples in this documentation use Titan `0.7.3` and require JDK 21 or
newer. Artifacts are published under `org.traffichunter.titan` on Maven Central.
