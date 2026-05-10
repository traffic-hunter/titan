# Server Example

Titan servers can be started from `titan-env.yml` through `TitanBootstrap`, or
embedded directly through the STOMP server API.

## Bootstrap From Configuration

Use this path for normal application startup.

### Dependencies

Gradle:

```kotlin
repositories {
    mavenCentral()
}
```

```kotlin
implementation("org.traffichunter.titan:titan-bootstrap:0.5.2")
implementation("org.traffichunter.titan:titan-core:0.5.2")
implementation("org.traffichunter.titan:titan-stomp:0.5.2")
implementation("org.traffichunter.titan:titan-fanout:0.5.2")
```

### `titan-env.yml`

```yaml
titan:
  servers:
    - name: stomp-dispatch
      protocol: stomp
      host: 0.0.0.0
      port: 61613
      transport-options:
        reuse-address: "true"
        child-tcp-no-delay: "true"
      protocol-options:
        supported-versions: "1.2"
        max-body-length: "1048576"
        heartbeat-x: "1000"
        heartbeat-y: "1000"
        fanout-mode: "virtual"
```

### Start

```java
import org.traffichunter.titan.bootstrap.TitanBootstrap;

public class TitanServerApplication {

    public static void main(String[] args) {
        TitanBootstrap.run("./titan-env.yml");
    }
}
```

## Embed A STOMP Server

Use this path for tests, experiments, or applications that need direct control
over the server instance.

```java
import java.util.concurrent.TimeUnit;
import org.traffichunter.titan.core.channel.EventLoopGroups;
import org.traffichunter.titan.core.transport.stomp.StompServer;
import org.traffichunter.titan.core.transport.stomp.option.StompServerOption;
import org.traffichunter.titan.fanout.FanoutGateway;
import org.traffichunter.titan.fanout.StompSendToFanoutHandler;
import org.traffichunter.titan.fanout.exporter.StompFanoutExporter;

public class EmbeddedStompServerExample {

    public static void main(String[] args) throws Exception {
        EventLoopGroups groups = EventLoopGroups.group(1, 4);
        StompServer server = StompServer.open(
                groups,
                StompServerOption.builder()
                        .heartbeatX(1000L)
                        .heartbeatY(1000L)
                        .build()
        );

        FanoutGateway fanoutGateway = FanoutGateway.ofVirtual(
                new StompFanoutExporter(server.connection())
        );

        try {
            server
                    .onStomp(handler -> handler
                            .sendHandler(new StompSendToFanoutHandler(fanoutGateway)))
                    .start()
                    .listen("0.0.0.0", 61613)
                    .get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            fanoutGateway.close();
            if (server.isStart()) {
                server.shutdown(30, TimeUnit.SECONDS);
            }
            throw e;
        }
    }
}
```

`StompServer.open(...).onStomp(...).onChannel(...).start()` configures the
embedded server fluently. `listen(...)` returns a promise so callers can wait
until the bind operation completes.

## Start An Embedded Server Asynchronously

`listen(...)` returns a Titan promise. Keep the promise asynchronous when the
application wants to react to bind completion without blocking the caller.

```java
import org.traffichunter.titan.core.channel.EventLoopGroups;
import org.traffichunter.titan.core.transport.stomp.StompServer;
import org.traffichunter.titan.core.transport.stomp.option.StompServerOption;

public class AsyncEmbeddedStompServerExample {

    public static void main(String[] args) {
        EventLoopGroups groups = EventLoopGroups.group(1, 4);
        StompServer server = StompServer.open(groups, StompServerOption.builder().build())
                .onStomp(handler -> handler);

        server.start()
                .listen("0.0.0.0", 61613)
                .addListener(result -> {
                    if (result.isSuccess()) {
                        System.out.println("Titan STOMP server is listening");
                        return;
                    }

                    result.error().printStackTrace();
                    server.shutdown();
                });
    }
}
```

For long-running applications, keep the process alive and shut the server down
from the application's lifecycle hook.
