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
 * <p>The record separates server definitions from process-wide facilities such
 * as monitoring, backup, and flow control. Server definitions are defensively
 * copied, and every optional section is normalized to a non-null disabled or
 * default value before settings reach runtime components.</p>
 */
public record Settings(
        List<ServerSettings> servers,
        MonitorSettings monitor,
        BackupSettings backup,
        FlowControlSettings flowControl
) {

    public Settings(
            @Nullable List<ServerSettings> servers,
            @Nullable MonitorSettings monitor,
            @Nullable BackupSettings backup
    ) {
        this(servers, monitor, backup, null);
    }

    public Settings(
            @Nullable List<ServerSettings> servers,
            @Nullable MonitorSettings monitor,
            @Nullable BackupSettings backup,
            @Nullable FlowControlSettings flowControl
    ) {
        this.servers = servers == null ? List.of() : List.copyOf(servers);
        this.monitor = monitor == null ? MonitorSettings.disabled() : monitor;
        this.backup = backup == null ? BackupSettings.disabled() : backup;
        this.flowControl = flowControl == null ? FlowControlSettings.disabled() : flowControl;
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

    /**
     * Process-wide admission control settings.
     *
     * <p>Resource-specific settings are nested so additional controls such as
     * CPU, thread, or queue pressure can be added without flattening unrelated
     * thresholds into this record.</p>
     */
    public record FlowControlSettings(
            boolean enabled,
            HeapFlowControlSettings heap
    ) {

        public static FlowControlSettings disabled() {
            return new FlowControlSettings(false, HeapFlowControlSettings.defaults());
        }

        public FlowControlSettings(
                boolean enabled,
                @Nullable HeapFlowControlSettings heap
        ) {
            this.enabled = enabled;
            this.heap = heap == null ? HeapFlowControlSettings.defaults() : heap;
        }
    }

    /** Heap usage hysteresis used to close and reopen message admission. */
    public record HeapFlowControlSettings(
            boolean enabled,
            double highWatermark,
            double lowWatermark
    ) {

        private static final double DEFAULT_HIGH_WATERMARK = 0.90;
        private static final double DEFAULT_LOW_WATERMARK = 0.70;

        public static HeapFlowControlSettings defaults() {
            return new HeapFlowControlSettings(
                    true,
                    DEFAULT_HIGH_WATERMARK,
                    DEFAULT_LOW_WATERMARK
            );
        }

        public HeapFlowControlSettings {
            if (highWatermark <= 0.0 || highWatermark > 1.0) {
                throw new IllegalArgumentException("Heap high watermark must be greater than 0 and at most 1");
            }
            if (lowWatermark < 0.0 || lowWatermark >= highWatermark) {
                throw new IllegalArgumentException("Heap low watermark must be at least 0 and lower than high watermark");
            }
        }
    }
}
