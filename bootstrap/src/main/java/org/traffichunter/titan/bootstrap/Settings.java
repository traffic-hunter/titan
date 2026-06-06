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

import java.util.List;
import lombok.Builder;

/**
 * Immutable runtime settings resolved from the bootstrap environment.
 *
 * <p>At the moment the primary setting is the list of server definitions. The
 * record defensively copies that list so downstream runtime code can safely
 * share a {@code Settings} instance without observing accidental caller-side
 * mutation.</p>
 */
@Builder
public record Settings(
        List<ServerSettings> servers,
        MonitorSettings monitor
) {

    public Settings {
        servers = servers == null ? List.of() : List.copyOf(servers);
        if (monitor == null) {
            monitor = MonitorSettings.disabled();
        }
    }

    public record MonitorSettings(
            boolean enabled,
            String host,
            int port,
            String token,
            int threadPoolSize
    ) {

        public static MonitorSettings disabled() {
            return new MonitorSettings(false, "127.0.0.1", 7777, "", 8);
        }

        public MonitorSettings {
            if (host == null || host.isBlank()) {
                host = "127.0.0.1";
            }
            if (port <= 0 || port > 65535) {
                port = 7777;
            }
            if (token == null) {
                token = "";
            }
            if (threadPoolSize <= 0) {
                threadPoolSize = 8;
            }
        }
    }
}
