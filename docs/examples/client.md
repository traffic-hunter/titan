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
implementation("org.traffichunter.titan:titan-client:0.8.0")
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
