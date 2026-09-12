# Titan Client Example

Use `titan-client` to connect to a Titan server directly without Spring.

## Dependency

Gradle:

```kotlin
repositories {
    mavenCentral()
}
```

```kotlin
implementation("org.traffichunter.titan:titan-client:0.8.3")
```

## Connect, Subscribe, Send

```java
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.traffichunter.titan.client.TitanClient;
import org.traffichunter.titan.core.codec.stomp.StompHeaders;
import org.traffichunter.titan.core.transport.stomp.option.StompSessionOption;

public class TitanClientExample {

    public static void main(String[] args) throws Exception {
        TitanClient client = TitanClient.builder()
                .worker(2)
                .host("127.0.0.1")
                .port(61613)
                .session(StompSessionOption.builder()
                        .login("guest")
                        .passcode("guest")
                        .virtualHost("guest")
                        .build())
                .build();

        try {
            client.start();
            client.connect().get(30, TimeUnit.SECONDS);

            client.subscribe(
                    "/notifications",
                    Map.of(StompHeaders.Elements.ID, "notifications"),
                    frame -> System.out.println(new String(frame.body(), StandardCharsets.UTF_8))
            ).get(30, TimeUnit.SECONDS);

            client.send("/notifications", "hello titan")
                    .get(30, TimeUnit.SECONDS);
        } finally {
            client.shutdown(30, TimeUnit.SECONDS);
        }
    }
}
```

The builder is the public configuration surface for both Titan's native client
and the Vert.x implementation. Keep one client for its full application
lifecycle and shut it down when it is no longer needed.

## Destination Groups

A group is a namespace for destinations, so several parts of an application can
use the same destination without their messages mixing. The wire rules are in
[Destination groups](../concepts/destinations.md#destination-groups).

Every send and subscribe operation has an overload that takes the group first:

```java
client.subscribe("market", "/orders", frame -> handleMarketOrder(frame))
        .get(30, TimeUnit.SECONDS);
client.subscribe("notification", "/orders", frame -> handleNotification(frame))
        .get(30, TimeUnit.SECONDS);

client.send("market", "/orders", "{\"id\":42}").get(30, TimeUnit.SECONDS);
```

- A `null` or blank group means the `default` group, and no `group` header is
  sent at all.
- A name outside `^[a-zA-Z0-9_-]{1,64}$` fails the returned future before the
  frame is built. A `Buffer` payload is still consumed exactly once.
- Naming a group and also passing a `group` header that resolves to a different
  name fails the same way. The header map you pass is never modified.

Each group subscribe gets its own generated subscription identifier, which is
what the returned future carries. Unsubscribe with that value:

```java
String id = client.subscribe("market", "/orders", handler).get(30, TimeUnit.SECONDS);
client.unsubscribe(id).get(30, TimeUnit.SECONDS);
```

Pass `StompHeaders.Elements.ID` yourself when an identifier has to be fixed. A
duplicate identifier is refused and the existing subscription stays.

The older `subscribe(destination, headers, handler)` overload predates groups and
still falls back to the destination as the subscription identifier. Subscribing to
one destination in two groups through that overload needs an explicit `ID` header
for each; the group overloads take care of it.

The deprecated Vert.x **server** transport does not support groups and answers a
`group` header with an `ERROR` frame. Both client implementations send the header,
so a grouped send against that server fails instead of quietly going to `default`.

## Select An Implementation

The native implementation is selected by default. Applications can select the
Vert.x driver without exposing a different client type:

```java
TitanClient client = TitanClient.builder()
        .implementation(TitanClient.Implementation.VERTX)
        .host("127.0.0.1")
        .port(61613)
        .build();
```

All send, subscribe, acknowledgement, and lifecycle operations remain on
`TitanClient`. Titan's `TlsContext` is currently supported only by the native
implementation.

## Configure Reconnection

The facade keeps logical subscription metadata so active subscriptions can be
restored after an unexpected connection loss. Configure the retry timing on the
builder:

```java
import java.time.Duration;
import org.traffichunter.titan.core.resilience.retry.RetryPolicy;

TitanClient client = TitanClient.builder()
        .host("127.0.0.1")
        .port(61613)
        .connectTimeout(Duration.ofSeconds(5))
        .reconnect(RetryPolicy.exponentialWithJitter(
                RetryPolicy.UNLIMITED_ATTEMPTS,
                Duration.ofSeconds(1),
                Duration.ofSeconds(30),
                2
        ))
        .build();
```

Use `connectionDroppedHandler`, `errorHandler`, and `exceptionHandler` when the
application needs lifecycle or failure notifications. A graceful
`disconnect()` does not trigger unexpected-loss recovery; `shutdown(...)`
stops reconnect work and releases the client runtime.

## Asynchronous Send

Client operations return `CompletableFuture`, so they can be composed without blocking.

```java
import java.util.concurrent.TimeUnit;
import org.traffichunter.titan.client.TitanClient;

public class AsyncTitanClientExample {

    public static void main(String[] args) {
        TitanClient client = TitanClient.builder()
                .worker(2)
                .host("127.0.0.1")
                .port(61613)
                .build();

        client.start();
        client.connect()
                .thenCompose(ignored -> client.send("/notifications", "hello titan"))
                .whenComplete((frame, error) -> {
                    try {
                        if (error != null) {
                            error.printStackTrace();
                            return;
                        }
                        System.out.println("message sent");
                    } finally {
                        client.shutdown(30, TimeUnit.SECONDS);
                    }
                });
    }
}
```
