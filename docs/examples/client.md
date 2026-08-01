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
implementation("org.traffichunter.titan:titan-client:0.7.4")
```

## Connect, Subscribe, Send

```java
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.traffichunter.titan.client.TitanClient;
import org.traffichunter.titan.core.codec.stomp.StompHeaders;
import org.traffichunter.titan.core.transport.stomp.option.StompSessionOption;
import org.traffichunter.titan.core.util.buffer.Buffer;

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

            client.send("/notifications", Buffer.alloc("hello titan"))
                    .get(30, TimeUnit.SECONDS);
        } finally {
            client.shutdown(30, TimeUnit.SECONDS);
        }
    }
}
```

The builder is the public configuration surface for both Titan's native client and the Vert.x
implementation. Keep the client for its full lifecycle and shut it down when it is no longer
needed.

## Asynchronous Send

Client operations return `CompletableFuture`, so they can be composed without blocking.

```java
import java.util.concurrent.TimeUnit;
import org.traffichunter.titan.client.TitanClient;
import org.traffichunter.titan.core.util.buffer.Buffer;

public class AsyncTitanClientExample {

    public static void main(String[] args) {
        TitanClient client = TitanClient.builder()
                .worker(2)
                .host("127.0.0.1")
                .port(61613)
                .build();

        client.start();
        client.connect()
                .thenCompose(ignored -> client.send(
                        "/notifications",
                        Buffer.alloc("hello titan")
                ))
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
