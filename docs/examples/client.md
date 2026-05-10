# STOMP Client Example

Use `titan-stomp` when you want to create a Titan STOMP client directly without
Spring.

## Dependency

Gradle:

```kotlin
repositories {
    mavenCentral()
}
```

```kotlin
implementation("org.traffichunter.titan:titan-stomp:0.5.2")
```

## Connect, Subscribe, Send

```java
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.traffichunter.titan.core.channel.EventLoopGroups;
import org.traffichunter.titan.core.channel.stomp.StompClientConnection;
import org.traffichunter.titan.core.codec.stomp.StompHeaders;
import org.traffichunter.titan.core.transport.stomp.StompClient;
import org.traffichunter.titan.core.transport.stomp.option.StompClientOption;
import org.traffichunter.titan.core.util.buffer.Buffer;

public class StompClientExample {

    public static void main(String[] args) throws Exception {
        EventLoopGroups groups = EventLoopGroups.group(1, 2);
        StompClient client = StompClient.open(groups, StompClientOption.builder()
                .host("127.0.0.1")
                .port(61613)
                .login("guest")
                .passcode("guest")
                .virtualHost("guest")
                .build())
                .onStomp(handler -> handler
                        .messageHandler((event, context) -> {
                            String payload = event.frame().getBody().toString(StandardCharsets.UTF_8);
                            System.out.println(payload);
                        }));

        try {
            client.start();

            StompClientConnection connection = client.connect(
                    "127.0.0.1",
                    61613,
                    30,
                    TimeUnit.SECONDS
            ).get(30, TimeUnit.SECONDS);

            StompHeaders subscribeHeaders = StompHeaders.create();
            subscribeHeaders.put(StompHeaders.Elements.ID, "notifications");

            connection.subscribe("/topic/notifications", subscribeHeaders)
                    .get(30, TimeUnit.SECONDS);

            connection.send("/topic/notifications", Buffer.alloc("hello titan"))
                    .get(30, TimeUnit.SECONDS);
        } finally {
            client.shutdown(30, TimeUnit.SECONDS);
        }
    }
}
```

`StompClient.open(...).onStomp(...).onChannel(...)` can be used to configure
the client fluently before `start()`. Create one `EventLoopGroups` per client
lifecycle and shut the client down when the application no longer needs the
connection.

## Asynchronous Send

`StompClientConnection` operations return Titan promises. You can keep them
asynchronous and attach listeners instead of blocking with `get(...)`.

```java
import java.util.concurrent.TimeUnit;
import org.traffichunter.titan.core.channel.EventLoopGroups;
import org.traffichunter.titan.core.channel.stomp.StompClientConnection;
import org.traffichunter.titan.core.codec.stomp.StompFrame;
import org.traffichunter.titan.core.concurrent.Promise;
import org.traffichunter.titan.core.transport.stomp.StompClient;
import org.traffichunter.titan.core.transport.stomp.option.StompClientOption;
import org.traffichunter.titan.core.util.buffer.Buffer;

public class AsyncStompClientExample {

    public static void main(String[] args) {
        EventLoopGroups groups = EventLoopGroups.group(1, 2);
        StompClient client = StompClient.open(groups, StompClientOption.builder()
                .host("127.0.0.1")
                .port(61613)
                .build());

        client.start();

        client.connect("127.0.0.1", 61613, 30, TimeUnit.SECONDS)
                .thenCompose(connection -> sendAsync(connection, "/topic/notifications", "hello titan"))
                .addListener(result -> {
                    if (result.isSuccess()) {
                        System.out.println("message sent");
                        return;
                    }

                    result.error().printStackTrace();
                });
    }

    private static Promise<StompFrame> sendAsync(
            StompClientConnection connection,
            String destination,
            String payload
    ) {
        return connection.send(destination, Buffer.alloc(payload));
    }
}
```

Use `future()` when integration code needs a JDK `Future`.

```java
var future = connection.send("/topic/notifications", Buffer.alloc("hello titan")).future();
```
