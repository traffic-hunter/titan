/*
 * Copyright 2025 traffic-hunter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traffichunter.titan.bootstrap;

import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Normalized configuration for one managed server.
 *
 * <p>The YAML layer largely preserves user input. This record applies defaults,
 * validates required values,
 * and separates shared options from transport- and protocol-specific overrides.</p>
 *
 * <p>Option resolution is shallow and deterministic. Values in
 * {@code transportOptions} override {@code options} for transport providers,
 * and values in {@code protocolOptions} override {@code options} for protocol
 * providers. Common settings can be declared once and overridden for each provider.</p>
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
