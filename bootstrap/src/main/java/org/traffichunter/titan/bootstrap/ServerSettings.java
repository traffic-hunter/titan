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

import java.util.LinkedHashMap;
import java.util.Map;

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
        Map<String, String> protocolOptions
) {

    public ServerSettings {
        if (name == null) {
            name = "";
        }
        if (transport == null) {
            transport = "";
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
}
