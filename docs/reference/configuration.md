# Configuration

Titan reads its standalone runtime configuration from `titan-env.yml`. Pass the
path with the `titan.environment.path` system property.

```bash
java -Dtitan.environment.path=./titan-env.yml -jar titan-server-0.8.0.jar
```

## Server

| Key | Purpose | Example |
| --- | --- | --- |
| `name` | Runtime server name | `stomp-dispatch` |
| `protocol` | Protocol engine | `stomp` |
| `host` | Bind address | `0.0.0.0` |
| `port` | Bind port | `61613` |
| `transport` | Network transport | `tcp` or `websocket` |
| `transport-options` | TCP and channel settings | See below |
| `protocol-options` | STOMP and fanout settings | See below |

## Common transport options

| Key | Purpose |
| --- | --- |
| `reuse-address` | Allow address reuse on the server socket |
| `child-tcp-no-delay` | Disable Nagle's algorithm on accepted connections |
| `path` | WebSocket HTTP upgrade path | `/stomp` |

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

## WebSocket

Use the `websocket` transport to carry STOMP frames through an HTTP upgrade.
The configured port accepts WebSocket connections at the selected path.

```yaml
titan:
  servers:
    - name: stomp-websocket
      transport: websocket
      protocol: stomp
      host: 0.0.0.0
      port: 8080
      transport-options:
        path: "/stomp"
      protocol-options:
        supported-versions: "1.2"
```

Native and Vert.x clients use the same STOMP behavior after the WebSocket
upgrade. Spring clients can connect with
`spring.titan.endpoint=ws://localhost:8080/stomp`.
Custom WebSocket clients must negotiate the `v12.stomp` subprotocol.
When `transport-options.path` is omitted or blank, Titan uses `/`. A configured
path may be written as either `stomp` or `/stomp`; Titan normalizes both to
`/stomp` before the HTTP upgrade.

## TLS

TLS settings are declared in the dedicated `tls` section. The key store must be
readable by the Titan process.

```yaml
titan:
  servers:
    - name: secure-stomp
      transport: tcp
      protocol: stomp
      host: 0.0.0.0
      port: 61614
      tls:
        side: server
        client-auth: none
        path: /etc/titan/server.p12
        type: PKCS12
        store-password: store-secret
        key-password: key-secret
        verify-hostname: false
```

| Key | Purpose | Default |
| --- | --- | --- |
| `side` | TLS endpoint role | `server` |
| `client-auth` | Client certificate policy: `none`, `want`, or `need` | `none` |
| `path` | PKCS12 or JKS key-store path | Required |
| `type` | Key-store format | `PKCS12` |
| `store-password` | Password used to open the key store | Empty |
| `key-password` | Password used to access the private key | Empty |
| `verify-hostname` | Enable endpoint identification where applicable | `false` |

## Monitor

| Key | Purpose | Example |
| --- | --- | --- |
| `enabled` | Start the monitoring service | `true` |
| `host` | Monitor bind address | `127.0.0.1` |
| `port` | Monitor HTTP port | `7777` |
| `token` | Optional access token | `change-me` |

See [Monitoring and CLI](../operate/monitoring.md) for endpoint and client
examples.
