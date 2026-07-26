/*
 * The MIT License
 *
 * Copyright (c) 2025 traffic-hunter
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.traffichunter.titan.bootstrap;

import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Normalized configuration for one managed server.
 *
 * <p>The YAML layer is permissive and mostly mirrors user input. This record is
 * the stricter runtime shape: it applies defaults, validates required values,
 * and separates shared options from transport- and protocol-specific overrides.</p>
 *
 * <p>Option resolution is shallow and deterministic. Values in
 * {@code transportOptions} override {@code options} for transport providers,
 * and values in {@code protocolOptions} override {@code options} for protocol
 * providers. This lets common settings be declared once while preserving a
 * precise override point for each provider layer.</p>
 */
@NullUnmarked
public record ServerSettings(
        String name,
        String transport,
        String protocol,
        String host,
        int port,
        int primaryThreads,
        int secondaryThreads,
        Map<String, String> options,
        Map<String, String> transportOptions,
        Map<String, String> protocolOptions,
        TlsSettings tls
) {

    public ServerSettings {
        if (name == null) {
            name = "";
        }
        if (transport == null || transport.isBlank()) {
            transport = "tcp";
        }
        if (protocol == null) {
            protocol = "";
        }
        if (host == null || host.isBlank()) {
            host = "0.0.0.0";
        }
        if (options == null) {
            options = Map.of();
        }
        if (transportOptions == null) {
            transportOptions = Map.of();
        }
        if (protocolOptions == null) {
            protocolOptions = Map.of();
        }
        if (tls == null) {
            tls = TlsSettings.disabled();
        }
        if (protocol.isBlank()) {
            throw new IllegalArgumentException("protocol cannot be blank");
        }
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("port must be in range 1..65535");
        }
        if (primaryThreads <= 0) {
            primaryThreads = 1;
        }
        if (secondaryThreads <= 0) {
            secondaryThreads = Math.max(1, Runtime.getRuntime().availableProcessors());
        }
        options = Map.copyOf(options);
        transportOptions = Map.copyOf(transportOptions);
        protocolOptions = Map.copyOf(protocolOptions);
    }

    public String serverName() {
        return name.isBlank() ? protocol + "-" + port : name;
    }

    public boolean hasTransport() {
        return !transport.isBlank();
    }

    public Map<String, String> resolvedTransportOptions() {
        return merge(options, transportOptions);
    }

    public Map<String, String> resolvedProtocolOptions() {
        return merge(options, protocolOptions);
    }

    private static Map<String, String> merge(Map<String, String> base, Map<String, String> overrides) {
        if (base.isEmpty() && overrides.isEmpty()) {
            return Map.of();
        }

        Map<String, String> merged = new LinkedHashMap<>(base);
        merged.putAll(overrides);
        return Map.copyOf(merged);
    }

    /**
     * Dedicated TLS settings for one server.
     *
     * <p>TLS is modeled separately from transport and protocol option maps because its
     * certificate material, endpoint role, and authentication policy require explicit
     * validation by the runtime.</p>
     */
    public record TlsSettings(
            boolean enabled,
            String side,
            String clientAuth,
            String path,
            String type,
            String storePassword,
            String keyPassword,
            boolean verifyHostname
    ) {

        public TlsSettings(
                boolean enabled,
                @Nullable String side,
                @Nullable String clientAuth,
                @Nullable String path,
                @Nullable String type,
                @Nullable String storePassword,
                @Nullable String keyPassword,
                boolean verifyHostname
        ) {
            this.enabled = enabled;
            this.side = side == null || side.isBlank() ? "server" : side;
            this.clientAuth = clientAuth == null || clientAuth.isBlank() ? "none" : clientAuth;
            this.path = path == null ? "" : path;
            this.type = type == null || type.isBlank() ? "PKCS12" : type;
            this.storePassword = storePassword == null ? "" : storePassword;
            this.keyPassword = keyPassword == null ? "" : keyPassword;
            this.verifyHostname = verifyHostname;
        }

        public static TlsSettings disabled() {
            return new TlsSettings(
                    false,
                    "server",
                    "none",
                    "",
                    "PKCS12",
                    "",
                    "",
                    false
            );
        }
    }
}
