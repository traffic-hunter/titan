# Configuration

Titan reads its standalone runtime configuration from `titan-env.yml`. Pass the
path with the `titan.environment.path` system property.

```bash
java -Dtitan.environment.path=./titan-env.yml -jar titan-server-0.7.3.jar
```

## Server

| Key | Purpose | Example |
| --- | --- | --- |
| `name` | Runtime server name | `stomp-dispatch` |
| `protocol` | Protocol engine | `stomp` |
| `host` | Bind address | `0.0.0.0` |
| `port` | Bind port | `61613` |
| `transport-options` | TCP and channel settings | See below |
| `protocol-options` | STOMP and fanout settings | See below |

## Common transport options

| Key | Purpose |
| --- | --- |
| `reuse-address` | Allow address reuse on the server socket |
| `child-tcp-no-delay` | Disable Nagle's algorithm on accepted connections |

Configuration option values are represented as strings in the current YAML
format.

## Common protocol options

| Key | Purpose | Example |
| --- | --- | --- |
| `supported-versions` | Accepted STOMP versions | `"1.2"` |
| `max-body-length` | Maximum frame body size in bytes | `"1048576"` |
| `heartbeat-x` | Outgoing heartbeat interval in milliseconds | `"1000"` |
| `heartbeat-y` | Expected incoming heartbeat interval in milliseconds | `"1000"` |
| `fanout-mode` | Optional fanout implementation | `"virtual"` |

Heartbeat values must be zero or greater. A zero value disables that heartbeat
direction.

## Monitor

| Key | Purpose | Example |
| --- | --- | --- |
| `enabled` | Start the monitoring service | `true` |
| `host` | Monitor bind address | `127.0.0.1` |
| `port` | Monitor HTTP port | `7777` |
| `token` | Optional access token | `change-me` |

See [Monitoring and CLI](../operate/monitoring.md) for endpoint and client
examples.
