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
curl 'http://localhost:7777/titan/monitor/queues?group=market'
```

Use the health endpoint for a lightweight availability check. Use snapshots for
the broader runtime view and the queues endpoint when investigating dispatcher
capacity or pressure.

A queue is named by its destination **group** and its destination together, so
`/orders` in `market` and `/orders` in `default` are two queues. Without a
`group` parameter the queues endpoint lists every group. With one it lists that
group alone, and a group holding no queues comes back as an empty list.

On a change (create, pause, resume, purge, delete) the `group` parameter names
the queue to act on. Leaving it out means the `default` group; a name outside
`^[a-zA-Z0-9_-]{1,64}$` is answered with `400` rather than being replaced by a
group the caller did not ask for. A change never creates a group it did not
find, except for creation, which brings the group into existence with the queue.

## Terminal dashboard

Prebuilt releases include `titan-cli-<version>-<os>-<arch>.tar.gz` archives.

```bash
tar -xzf titan-cli-0.8.3-linux-amd64.tar.gz
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
./titan --addr http://localhost:7777 --view queues --group market
./titan --addr http://localhost:7777 --view jvm --interval 1s --timeout 3s
./titan --addr http://localhost:7777 --no-color --once
```

The queue table has a `GROUP` column and sorts by group, then destination, so
two queues that share a destination stay apart. `--group` narrows the table to
one group; the JVM and channel figures stay server-wide and are labelled as
such.

## Manage queues

When the monitor is protected, provide its token through the environment:

```bash
export TITAN_MONITOR_TOKEN=<monitor-token>
./titan --addr http://localhost:7777 queue list
./titan --addr http://localhost:7777 queue create /orders --max-pending-bytes 1048576
./titan --addr http://localhost:7777 queue delete /orders
```

Every queue subcommand takes `--group`:

```bash
./titan --addr http://localhost:7777 queue list --group market
./titan --addr http://localhost:7777 queue create /orders --group market --max-pending-bytes 67108864
./titan --addr http://localhost:7777 queue pause /orders --group market
./titan --addr http://localhost:7777 queue resume /orders --group market
./titan --addr http://localhost:7777 queue purge /orders --group market
./titan --addr http://localhost:7777 queue delete /orders --group market
```

`queue list` without `--group` lists every group. A change without `--group`
targets the `default` group, so confirm the group before deleting: `/orders` in
`market` is not the `/orders` you see in `default`. Results name the queue as
`group:destination` in full, even where the table had to truncate a column.

The interactive `Management` menu asks for the group before the destination.
Leaving it empty means the default group for a change and every group for a
listing.

Deleting a queue affects live runtime state. Inspect it first and reserve
`--force` for cases where dropping active state is intentional. Deleting the
last queue of a group leaves the group behind with nothing in it, and an empty
group is invisible here: the `N queues in N groups` summary counts the groups
that currently hold a queue. Each queue carries its own pending messages,
consumer, and byte limit, so a destination held in ten groups is ten times the
runtime cost of one. See
[what a group costs](../concepts/destinations.md#what-a-group-costs).

## Keep the server, CLI, and runner in step

The server, the CLI, and the performance runner share one contract for queue
groups and are released together. The CLI refuses a queue listing whose entries
carry no group rather than guessing `default`, because guessing would show one
queue under another queue's name. Upgrade all three at once; a CLI that reports
`update the Titan server and CLI together` is talking to a server that predates
groups.

The performance test names a group as well:

```bash
./titan perf-test --group market --destination /queue/perf-test
```

The subscription, the warm-up publishes, and the measured publishes all use that
one group, and the report prints the `group:destination` it measured.
