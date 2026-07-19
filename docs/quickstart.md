# Quickstart

Run a Titan server and verify it from the monitoring API.

## Requirements

* JDK 21 or newer
* A Titan `0.7.3` standalone server JAR from GitHub Releases

## 1. Create the environment

Create `titan-env.yml` next to the server JAR:

```yaml
titan:
  monitor:
    enabled: true
    host: 127.0.0.1
    port: 7777
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

## 2. Start Titan

```bash
java -Dtitan.environment.path=./titan-env.yml \
  -jar titan-server-0.7.3.jar
```

Titan now accepts STOMP connections on `61613` and exposes its local monitor on
`127.0.0.1:7777`.

## 3. Verify the node

```bash
curl http://localhost:7777/titan/monitor/health
curl http://localhost:7777/titan/monitor/snapshot
```

## 4. Send your first message

Choose the client path that matches your application:

* [Native STOMP client](examples/client.md) for direct lifecycle control
* [Spring Boot](examples/spring-client.md) for `TitanTemplate` and
  `@TitanListener`

Use `/notifications` in both the subscriber and publisher examples to see
a message travel through the same destination.

## Next steps

* Learn [how Titan works](concepts/how-titan-works.md).
* Understand [dispatch routing](concepts/destinations.md).
* Add terminal visibility with [Monitoring and CLI](operate/monitoring.md).
