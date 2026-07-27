# Project scope

Titan's production focus is real-time STOMP dispatch over TCP and WebSocket.

## In scope

* STOMP server and client connections
* TCP and WebSocket server and client transport
* TLS-protected transport
* Exact destination routing through per-destination FIFO dispatcher queues
* In-memory fanout delivery
* Native Java and Spring Boot clients
* Local JVM, connection, server, and dispatcher visibility
* Embeddable runtime and SPI-based engine discovery

## Not a current promise

Titan does not currently promise:

* Durable message storage across node restarts
* Historical replay or long-term retention
* A drop-in replacement for a mature message broker
* Fully evolved retry, nack, and listener error policies in every client path
* Multi-node consensus or replicated queue state

These boundaries are intentional documentation, not hidden caveats. Evaluate
delivery and recovery requirements before placing Titan on a critical message
path.

## Compatibility baseline

* JDK 21 or newer
* STOMP 1.2 as the primary configured protocol version
* Go 1.22 or newer when building `titan-cli` from source

For project development and test commands, see the repository's root README and
[Contributing](../guide/CONTRIBUTING.md).
