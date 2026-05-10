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
implementation("org.traffichunter.titan:titan-spring-client:0.5.2")
```

## Enable Titan

```java
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.traffichunter.titan.springframework.stomp.annotation.EnableTitan;

@EnableTitan
@SpringBootApplication
public class Application {

    public static void main(String[] args) {
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
    host: 127.0.0.1
    port: 61613
    login: guest
    passcode: guest
    virtual-host: guest
    connect-timeout-millis: 30000
    heartbeat-x: 1000
    heartbeat-y: 1000
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
        titanTemplate.send("/topic/notifications", payload);
    }
}
```

## Send Messages Asynchronously

```java
import java.util.concurrent.Future;
import org.springframework.stereotype.Service;
import org.traffichunter.titan.core.codec.stomp.StompFrame;
import org.traffichunter.titan.springframework.stomp.TitanTemplate;

@Service
public class AsyncNotificationPublisher {

    private final TitanTemplate titanTemplate;

    public AsyncNotificationPublisher(TitanTemplate titanTemplate) {
        this.titanTemplate = titanTemplate;
    }

    public Future<StompFrame> publish(String payload) throws Exception {
        return titanTemplate.async().send("/topic/notifications", payload);
    }
}
```

## Receive Messages

```java
import org.springframework.stereotype.Component;
import org.traffichunter.titan.springframework.stomp.annotation.TitanListener;

@Component
public class NotificationListener {

    @TitanListener(destination = "/topic/notifications")
    public void onMessage(String payload) {
        System.out.println(payload);
    }
}
```

Use `TitanTemplate` for imperative send/subscribe operations and
`TitanTemplate.async()` when the caller wants a `Future`. Use `@TitanListener`
for annotation-driven message handling.
