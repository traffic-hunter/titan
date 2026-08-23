# Monitoring and CLI

Titan exposes a local HTTP monitor and a terminal-first CLI for inspecting a
running node.

## Enable the monitor

```yaml
titan:
  monitor:
    enabled: true
    host: 127.0.0.1
    port: 7777
    # token: change-me
```

Keep the monitor bound to a private or loopback interface unless you have added
appropriate network controls and authentication.

## HTTP endpoints

```bash
curl http://localhost:7777/titan/monitor/health
curl http://localhost:7777/titan/monitor/snapshot
curl http://localhost:7777/titan/monitor/queues
```

Use the health endpoint for a lightweight availability check. Use snapshots for
the broader runtime view and the queues endpoint when investigating dispatcher
capacity or pressure.

## Terminal dashboard

Prebuilt releases include `titan-cli-<version>-<os>-<arch>.tar.gz` archives.

```bash
tar -xzf titan-cli-0.8.0-linux-amd64.tar.gz
./titan --addr http://localhost:7777
```

The CLI is also published as `ghcr.io/traffic-hunter/titan-cli`. When Titan is
running through this repository's Compose configuration, start the dashboard
with:

```bash
docker compose --profile tools run --rm titan-cli
```

For standalone containers, place the server and CLI on the same Docker network
and address the server by its container name:

```bash
docker network create titan
docker run --detach --name titan --network titan \
  -p 61613:61613 -p 127.0.0.1:7777:7777 \
  ghcr.io/traffic-hunter/titan:latest
docker run --rm -it --network titan \
  ghcr.io/traffic-hunter/titan-cli:latest \
  --addr http://titan:7777
```

Select a view or produce automation-friendly output:

```bash
./titan --addr http://localhost:7777 --view queues
./titan --addr http://localhost:7777 --view jvm --interval 1s --timeout 3s
./titan --addr http://localhost:7777 --no-color --once
```

## Manage queues

When the monitor is protected, provide its token through the environment:

```bash
export TITAN_MONITOR_TOKEN=<monitor-token>
./titan --addr http://localhost:7777 queue list
./titan --addr http://localhost:7777 queue create /orders --max-pending-bytes 1048576
./titan --addr http://localhost:7777 queue delete /orders
```

Deleting a queue affects live runtime state. Inspect it first and reserve
`--force` for cases where dropping active state is intentional.
