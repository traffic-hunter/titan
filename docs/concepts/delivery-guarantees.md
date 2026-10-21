# Delivery guarantees

Titan provides live, in-memory dispatch with an **at-most-once delivery
attempt**. For each message accepted by a destination queue, Titan makes no
more than one delivery attempt to each matching subscription. It does not keep
the message for acknowledgement-based redelivery.

This is not an exactly-once guarantee. A producer that retries an uncertain
request can publish the same application event more than once, and Titan does
not deduplicate those requests.

## What success means

Message processing crosses several boundaries. Success at one boundary does
not imply success at the next one.

| Boundary | What success confirms | What it does not confirm |
| --- | --- | --- |
| `send` without a STOMP `receipt` header | The client wrote the SEND frame to its connection | Server acceptance, queue admission, or subscriber delivery |
| STOMP `RECEIPT` for SEND | The destination queue accepted the message and dispatch was activated | A subscriber received, processed, or acknowledged the MESSAGE frame |
| Queue admission | The message entered the in-memory destination queue | Persistence or recovery after process failure |
| Fanout attempt | Titan handed a MESSAGE frame to each writable matching connection it could reach | Completion of the socket write or application processing |
| Subscriber `ACK` or `NACK` | Titan accepted the protocol command | Message retention, retry, or redelivery |

The native client maps a confirmed queue refusal carrying the request's
`receipt-id` to `DispatchRejectException`. Without a receipt header, the send
future completes after the client-side socket write; a later server ERROR is
reported through the error handler instead.

## Queue refusal

A message is not admitted when its destination queue is full, paused, closed,
or when the message is larger than the queue byte limit. Titan sends an ERROR
frame and closes the connection. If the SEND included a receipt header, the
ERROR carries the same value as `receipt-id`, allowing the native client to
fail the matching request future.

Titan does not retry a refused message. The producer decides whether retrying
is appropriate for the application.

## Fanout and slow consumers

The destination consumer removes messages from its FIFO queue one at a time.
For each message, the STOMP exporter takes a snapshot of matching active
subscriptions and schedules one send attempt per subscription.

A subscription is skipped when its channel is not writable or its event loop
cannot accept the attempt before the export deadline. A failed or skipped
attempt does not hold the destination queue, and the message is not placed back
in the queue. Other writable subscribers can still receive their copy.

If no subscription matches, the accepted message leaves the queue without a
delivery. Titan does not retain it for a subscriber that connects later.

## Ordering

Each group and destination has one FIFO queue and one queue-draining consumer.
Titan starts delivery attempts in queue order for that queue. It does not
define ordering across destinations, across groups, or across a disconnect and
an application retry.

## Reconnection

The Titan client reconnects after an unexpected connection loss and restores
its active subscriptions. Reconnection restores subscription intent, not
message history:

* messages sent while a subscription is offline are not replayed;
* sends attempted without an active connection fail rather than wait for
  reconnection;
* an in-flight send can have an uncertain outcome when the connection fails;
* Titan does not automatically retry or deduplicate application messages.

Applications that need durable retention, redelivery after NACK, replay, or
exactly-once processing should place those guarantees outside Titan or use a
durable messaging system.

## Requesting admission confirmation

Add a STOMP receipt header when the producer must know whether Titan accepted
the SEND into its destination queue:

```java
import java.util.Map;
import org.traffichunter.titan.core.codec.stomp.StompHeaders.Elements;
import org.traffichunter.titan.core.util.IdGenerator;
import org.traffichunter.titan.core.util.buffer.Buffer;

client.send(
        "/notifications",
        Buffer.heap().alloc("hello"),
        Map.of(Elements.RECEIPT, IdGenerator.uuid())
);
```

The resulting future confirms admission only. End-to-end processing requires
an application-level acknowledgement from the subscriber.
