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

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * Address of a STOMP service and the transport used to reach it.
 *
 * <p>TCP endpoints carry only a host and port. WebSocket endpoints additionally
 * carry the HTTP upgrade path. Authentication and STOMP virtual-host values do
 * not belong to the endpoint and remain client options.</p>
 *
 * @author yun
 */
public record StompEndpoint(
        Scheme scheme,
        String host,
        int port,
        String path
) {

    public static final int DEFAULT_STOMP_PORT = 61613;
    public static final int DEFAULT_WEBSOCKET_PORT = 8080;
    public static final int DEFAULT_SECURE_WEBSOCKET_PORT = 443;

    public StompEndpoint {
        if (host.isBlank()) {
            throw new IllegalArgumentException("Endpoint host cannot be blank");
        }
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("Endpoint port must be in range 1..65535");
        }

        if (scheme.isWebSocket()) {
            if (path.isBlank()) {
                path = "/";
            } else if (!path.startsWith("/")) {
                throw new IllegalArgumentException("WebSocket endpoint path must start with '/'");
            }
        } else if (!path.isEmpty()) {
            throw new IllegalArgumentException("TCP endpoint cannot have a path");
        }
    }

    /** Parses {@code tcp}, {@code ws}, or {@code wss} endpoint syntax and supplies default ports. */
    public static StompEndpoint parse(String endpoint) {
        URI uri;
        try {
            uri = new URI(endpoint);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid STOMP endpoint: " + endpoint, e);
        }

        Scheme scheme = Scheme.from(uri.getScheme());
        if (uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("STOMP endpoint cannot contain user info, query, or fragment");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("STOMP endpoint must contain a host");
        }

        int port = uri.getPort() == -1 ? scheme.defaultPort() : uri.getPort();
        String path = uri.getRawPath();
        return new StompEndpoint(scheme, host, port, path == null ? "" : path);
    }

    /** Creates a plain TCP endpoint without an HTTP path. */
    public static StompEndpoint tcp(String host, int port) {
        return new StompEndpoint(Scheme.TCP, host, port, "");
    }

    /** Creates a non-secure WebSocket endpoint with an HTTP upgrade path. */
    public static StompEndpoint webSocket(String host, int port, String path) {
        return new StompEndpoint(Scheme.WS, host, port, path);
    }

    /** Converts the endpoint host and port to a socket address. */
    public InetSocketAddress socketAddress() {
        return new InetSocketAddress(host, port);
    }

    /** Returns whether this endpoint requires WebSocket framing. */
    public boolean isWebSocket() {
        return scheme.isWebSocket();
    }

    /** Returns whether this endpoint uses WebSocket over TLS. */
    public boolean isSecure() {
        return scheme == Scheme.WSS;
    }

    /** Converts this endpoint to its normalized URI representation. */
    public URI uri() {
        try {
            return new URI(scheme.value(), null, host, port, path, null, null);
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Failed to create STOMP endpoint URI", e);
        }
    }

    @Override
    public String toString() {
        return uri().toString();
    }

    /** Supported endpoint schemes and their default ports. */
    public enum Scheme {
        TCP("tcp", DEFAULT_STOMP_PORT),
        WS("ws", DEFAULT_WEBSOCKET_PORT),
        WSS("wss", DEFAULT_SECURE_WEBSOCKET_PORT),
        ;

        private final String value;
        private final int defaultPort;

        Scheme(String value, int defaultPort) {
            this.value = value;
            this.defaultPort = defaultPort;
        }

        public static Scheme from(@Nullable String value) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("STOMP endpoint scheme is required");
            }

            String normalized = value.toLowerCase(Locale.ROOT);
            for (Scheme scheme : values()) {
                if (scheme.value.equals(normalized)) {
                    return scheme;
                }
            }
            throw new IllegalArgumentException("Unsupported STOMP endpoint scheme: " + value);
        }

        public String value() {
            return value;
        }

        public int defaultPort() {
            return defaultPort;
        }

        public boolean isWebSocket() {
            return this == WS || this == WSS;
        }
    }
}
