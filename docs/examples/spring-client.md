# Spring Client Example

Use `titan-spring-client` when a Spring Boot application needs to connect to a
running Titan STOMP server.

## Dependency

Gradle:

```kotlin
repositories {
    mavenCentral()
}
```

```kotlin
implementation("org.traffichunter.titan:titan-spring-client:0.8.3")
```

## Enable Titan

```java
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.traffichunter.titan.springframework.stomp.annotation.EnableTitan;

@EnableTitan
@SpringBootApplication
public class Application {

    static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

## Configure Connection

```yaml
spring:
  titan:
    auto-start: true
    auto-connect: true
    client: titan # titan or vertx
    endpoint: ws://127.0.0.1:8080/stomp
    login: guest
    passcode: guest
    virtual-host: guest
    connect-timeout-millis: 30000
    heartbeat-x: 1000
    heartbeat-y: 1000
```

`spring.titan.endpoint` is the preferred connection setting. It accepts
`tcp://host:port`, `ws://host:port/path`, and `wss://host:port/path` and takes
precedence over the legacy host, port, transport, and WebSocket path properties.

## Send Messages

```java
import org.springframework.stereotype.Service;
import org.traffichunter.titan.springframework.stomp.core.TitanTemplate;

@Service
public class NotificationPublisher {

    private final TitanTemplate titanTemplate;

    public NotificationPublisher(TitanTemplate titanTemplate) {
        this.titanTemplate = titanTemplate;
    }
    
    public void publish(String payload) throws Exception {
        titanTemplate.send("/notifications", payload);
    }
}
```

## Send Messages Asynchronously

```java
import java.util.concurrent.CompletableFuture;
import org.springframework.stereotype.Service;
import org.traffichunter.titan.core.codec.stomp.StompFrames;
import org.traffichunter.titan.core.util.buffer.Buffer;
import org.traffichunter.titan.springframework.stomp.core.TitanTemplate;

@Service
public class AsyncNotificationPublisher {

    private final TitanTemplate titanTemplate;

    public AsyncNotificationPublisher(TitanTemplate titanTemplate) {
        this.titanTemplate = titanTemplate;
    }

    public CompletableFuture<StompFrames> publish(String payload) {
        return titanTemplate.send("/notifications", Buffer.heap().alloc(payload));
    }
}
```

## Receive Messages

```java
import org.springframework.stereotype.Component;
import org.traffichunter.titan.springframework.stomp.annotation.TitanListener;

@Component
public class NotificationListener {

    @TitanListener(destination = "/notifications")
    public void onMessage(String payload) {
        System.out.println(payload);
    }
}
```

`TitanTemplate` implements the asynchronous `StompOperations` contract directly.
Operations accepting a Titan `Buffer` return `CompletableFuture`; the String,
byte-array, and `ByteBuffer` send overloads are blocking conveniences. Use
`@TitanListener` for annotation-driven message handling.

## Destination Groups

`@TitanListener` subscribes within a destination group and `TitanTemplate`
publishes to one. A blank group means `default`. The wire rules are in
[Destination groups](../concepts/destinations.md#destination-groups).

```java
import org.springframework.stereotype.Component;
import org.traffichunter.titan.springframework.stomp.annotation.TitanListener;

@Component
public class OrderListeners {

    @TitanListener(group = "market", destination = "/orders")
    public void onMarketOrder(String payload) {
    }

    @TitanListener(group = "notification", destination = "/orders")
    public void onNotificationOrder(String payload) {
    }
}
```

The group accepts a property reference, which is resolved against the Spring
environment:

```java
@TitanListener(group = "${titan.order-group}", destination = "/orders")
public void onOrder(String payload) {
}
```

A placeholder nothing resolves, or a name outside `^[a-zA-Z0-9_-]{1,64}$`, fails
while listeners are being discovered rather than at the first SUBSCRIBE.

Publishing and programmatic subscribing name the group first:

```java
template.send("market", "/orders", "{\"id\":42}");
template.send("market", "/orders", payload);
template.subscribe("market", "/orders", frame -> handle(frame));
```

Each listener keeps the subscription identifier the client assigned it, so
stopping one listener leaves the other listeners on the same destination running.
Stopping a listener while the connection is down drops its subscription too, so a
later reconnect does not bring it back.

## Lifecycle And Acknowledgement

`spring.titan.client` selects the STOMP client implementation. `titan` uses the
native Titan client, while `vertx` uses the Vert.x STOMP client adapter. Both are
exposed through the same Spring API.

When `auto-start` is enabled, the Spring context starts the configured client.
When `auto-connect` is enabled, the manager connects during startup. If a caller
uses `TitanTemplate` or a listener before a connection exists, the manager starts
and connects the client before resolving operations.

`@TitanListener` acknowledges `MESSAGE` frames after the listener method returns
successfully. If listener invocation fails, the configured error handler is
called and the frame is negatively acknowledged when a `message-id` header is
available.
When the endpoint property is absent,
`spring.titan.transport=websocket` upgrades the selected client connection at
`spring.titan.websocket-path`. The host and port then come from
`spring.titan.host` and `spring.titan.port`.

## TLS With PKCS12

Secure WebSocket connections use a named Spring SSL bundle. Titan receives the
key and trust managers prepared by Spring, so certificate locations and passwords
remain in Spring Boot's standard SSL configuration.

```yaml
spring:
  titan:
    client: titan
    endpoint: wss://localhost:8443/stomp
    ssl:
      bundle: titan-client
      verify-hostname: true
  ssl:
    bundle:
      jks:
        titan-client:
          truststore:
            location: classpath:titan-client.p12
            password: secret
            type: PKCS12
```

Providing the bundle for a TCP endpoint enables TLS directly over that TCP
connection. Spring SSL bundles are currently supported by the native Titan
client; the Vert.x client adapter remains available for non-TLS connections.
