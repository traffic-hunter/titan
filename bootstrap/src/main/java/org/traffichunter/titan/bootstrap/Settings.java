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
import org.jspecify.annotations.Nullable;

/**
 * Immutable runtime settings resolved from the bootstrap environment.
 *
 * <p>At the moment the primary setting is the list of server definitions. The
 * record defensively copies that list so downstream runtime code can safely
 * share a {@code Settings} instance without observing accidental caller-side
 * mutation.</p>
 */
public record Settings(
        List<ServerSettings> servers,
        MonitorSettings monitor,
        BackupSettings backup
) {

    public Settings(
            @Nullable List<ServerSettings> servers,
            @Nullable MonitorSettings monitor,
            @Nullable BackupSettings backup
    ) {
        this.servers = servers == null ? List.of() : List.copyOf(servers);
        this.monitor = monitor == null ? MonitorSettings.disabled() : monitor;
        this.backup = backup == null ? BackupSettings.disabled() : backup;
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

        public MonitorSettings(
                boolean enabled,
                @Nullable String host,
                int port,
                @Nullable String token,
                int threadPoolSize
        ) {
            this.enabled = enabled;
            this.host = host == null || host.isBlank() ? "127.0.0.1" : host;
            this.port = port <= 0 || port > 65535 ? 7777 : port;
            this.token = token == null ? "" : token;
            this.threadPoolSize = threadPoolSize <= 0 ? 8 : threadPoolSize;
        }
    }

    public record BackupSettings(
            boolean enabled,
            String type,
            String path,
            String syncPolicy,
            String recoveryPolicy
    ) {

        public static BackupSettings disabled() {
            return new BackupSettings(false, "aof", "", "every_sec", "load_truncated_tail");
        }

        public static BackupSettings fromConfig(
                boolean enabled,
                @Nullable String type,
                @Nullable String path,
                @Nullable String syncPolicy,
                @Nullable String recoveryPolicy
        ) {
            return new BackupSettings(enabled, type, path, syncPolicy, recoveryPolicy);
        }

        public BackupSettings(
                boolean enabled,
                @Nullable String type,
                @Nullable String path,
                @Nullable String syncPolicy,
                @Nullable String recoveryPolicy
        ) {
            this.enabled = enabled;
            this.type = type == null || type.isBlank() ? "aof" : type;
            this.path = path == null || path.isBlank() ? "" : path;
            this.syncPolicy = syncPolicy == null || syncPolicy.isBlank() ? "every_sec" : syncPolicy;
            this.recoveryPolicy = recoveryPolicy == null || recoveryPolicy.isBlank()
                    ? "load_truncated_tail"
                    : recoveryPolicy;
        }
    }
}
