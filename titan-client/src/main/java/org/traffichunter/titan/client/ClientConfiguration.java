/*
The MIT License

Copyright (c) 2025 traffic-hunter

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
*/
package org.traffichunter.titan.client;

import java.time.Duration;

import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.codec.stomp.StompVersion;
import org.traffichunter.titan.core.net.TlsContext;
import org.traffichunter.titan.core.resilience.retry.RetryListener;
import org.traffichunter.titan.core.resilience.retry.RetryPolicy;
import org.traffichunter.titan.core.transport.option.InetClientOption;
import org.traffichunter.titan.core.transport.stomp.option.StompSessionOption;

/**
 * Immutable configuration shared by a {@link TitanClient} facade and its client driver.
 *
 * <p>The public {@link TitanClient.Builder} is the preferred configuration entry point. This
 * record is also exposed for driver implementations and integrations that need to assemble a
 * client explicitly. The compact constructor supplies defaults for omitted optional values and
 * validates values that would make a connection attempt invalid.</p>
 *
 * @param host remote server host used by the transport
 * @param port remote server port
 * @param session STOMP framing, headers, version, and heartbeat settings
 * @param inet native socket settings or their Vert.x equivalents
 * @param connectTimeout maximum duration of one connection attempt
 * @param reconnectPolicy delay and attempt policy used after connection loss
 * @param reconnectListener observer notified about reconnect attempts
 * @param tlsContext optional client-side TLS context applied before the native transport starts
 * @param webSocketPath optional HTTP path that selects WebSocket transport
 *
 * @author yun
 */
public record ClientConfiguration(
        String host,
        int port,
        StompSessionOption session,
        InetClientOption inet,
        Duration connectTimeout,
        RetryPolicy reconnectPolicy,
        RetryListener reconnectListener,
        @Nullable TlsContext tlsContext,
        @Nullable String webSocketPath
) {
    static final int DEFAULT_PORT = 61613;
    static final String DEFAULT_HOST = "127.0.0.1";
    static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(5);
    static final RetryPolicy DEFAULT_RECONNECT_POLICY = RetryPolicy.exponentialWithJitter(
            RetryPolicy.UNLIMITED_ATTEMPTS,
            Duration.ofSeconds(1),
            Duration.ofSeconds(30),
            2
    );
    static final ClientConfiguration DEFAULT = builder().build();

    static Builder builder() {
        return new Builder();
    }

    /** Validates connection values after the record components have been initialized. */
    public ClientConfiguration {
        if (host.isBlank()) {
            throw new IllegalArgumentException("host cannot be blank");
        }
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("port must be in range 1..65535");
        }
        if (connectTimeout.isNegative() || connectTimeout.isZero()) {
            throw new IllegalArgumentException("connectTimeout must be positive");
        }
        if (connectTimeout.toMillis() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("connectTimeout must not exceed Integer.MAX_VALUE milliseconds");
        }
        if (webSocketPath != null && (webSocketPath.isBlank() || !webSocketPath.startsWith("/"))) {
            throw new IllegalArgumentException("WebSocket path must start with '/'");
        }
    }

    /** Returns the optional CONNECT login from the session settings. */
    @Nullable String login() {
        return session.login();
    }

    /** Returns the optional CONNECT passcode from the session settings. */
    @Nullable String passcode() {
        return session.passcode();
    }

    /** Returns whether outbound frames should calculate {@code content-length}. */
    boolean autoComputeContentLength() {
        return session.autoComputeContentLength();
    }

    /** Returns whether the Vert.x adapter should use its STOMP frame write path. */
    boolean useStompFrame() {
        return session.useStompFrame();
    }

    /** Returns whether the CONNECT host header should be omitted. */
    boolean bypassHostHeader() {
        return session.bypassHostHeader();
    }

    /** Returns the outgoing heartbeat negotiation value in milliseconds. */
    long heartbeatX() {
        return session.heartbeatX();
    }

    /** Returns the incoming heartbeat negotiation value in milliseconds. */
    long heartbeatY() {
        return session.heartbeatY();
    }

    /** Returns the optional STOMP virtual host. */
    @Nullable String virtualHost() {
        return session.virtualHost();
    }

    /** Returns the maximum decoded STOMP frame size. */
    int maxFrameLength() {
        return session.maxFrameLength();
    }

    /** Returns the negotiated STOMP protocol version. */
    StompVersion stompVersion() {
        return session.version();
    }

    /** Returns socket options consumed by the selected client implementation. */
    InetClientOption inetClientOption() {
        return inet;
    }

    /** Minimal internal builder retained for direct implementation tests. */
    static final class Builder {

        private String host = DEFAULT_HOST;
        private int port = DEFAULT_PORT;
        private RetryPolicy reconnectPolicy = DEFAULT_RECONNECT_POLICY;
        private @Nullable String webSocketPath;

        Builder host(String host) {
            this.host = host;
            return this;
        }

        Builder port(int port) {
            this.port = port;
            return this;
        }

        Builder reconnectPolicy(RetryPolicy reconnectPolicy) {
            this.reconnectPolicy = reconnectPolicy;
            return this;
        }

        Builder webSocket(String path) {
            this.webSocketPath = path;
            return this;
        }

        ClientConfiguration build() {
            return new ClientConfiguration(
                    host,
                    port,
                    StompSessionOption.builder().build(),
                    InetClientOption.DEFAULT_INET_CLIENT_OPTION,
                    DEFAULT_CONNECT_TIMEOUT,
                    reconnectPolicy,
                    RetryListener.NOOP,
                    null,
                    webSocketPath
            );
        }
    }
}
