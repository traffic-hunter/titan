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
implementation("org.traffichunter.titan:titan-spring-client:0.7.4")
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
    retry:
      enabled: false
      type: exp # fix or exp
      max-attempts: 3
      delay: 1s
      max-delay: 30s
      multiplier: 2
    reconnect:
      enabled: true
```

## Send Messages

```java
import org.springframework.stereotype.Service;
import org.traffichunter.titan.springframework.stomp.TitanTemplate;

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
import java.util.concurrent.Future;
import org.springframework.stereotype.Service;
import org.traffichunter.titan.core.codec.stomp.StompFrames;
import org.traffichunter.titan.springframework.stomp.TitanTemplate;

@Service
public class AsyncNotificationPublisher {

    private final TitanTemplate titanTemplate;

    public AsyncNotificationPublisher(TitanTemplate titanTemplate) {
        this.titanTemplate = titanTemplate;
    }

    public Future<StompFrames> publish(String payload) throws Exception {
        return titanTemplate.async().send("/notifications", payload);
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

Use `TitanTemplate` for imperative send/subscribe operations and
`TitanTemplate.async()` when the caller wants a `Future`. Use `@TitanListener`
for annotation-driven message handling.

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
`spring.titan.transport=websocket` upgrades the selected client connection at
`spring.titan.websocket-path`. The host and port continue to come from
`spring.titan.host` and `spring.titan.port`.

`spring.titan.endpoint` is the preferred connection setting and accepts
`tcp://host:port`, `ws://host:port/path`, or `wss://host:port/path`. When present, it takes precedence
over the separate host, port, transport, and WebSocket path properties. The
legacy properties remain available for compatibility.

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
