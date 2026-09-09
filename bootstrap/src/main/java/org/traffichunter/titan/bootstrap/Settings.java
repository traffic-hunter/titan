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

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Immutable runtime settings resolved from the bootstrap environment.
 *
 * <p>Server definitions are separate from process-wide monitoring, backup, and
 * flow control settings. The constructor copies server definitions and replaces
 * omitted sections with non-null disabled or default values.</p>
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
            HeapFlowControlSettings heap,
            QueueFlowControlSettings queue
    ) {

        public static FlowControlSettings disabled() {
            return new FlowControlSettings(
                    false,
                    HeapFlowControlSettings.defaults(),
                    QueueFlowControlSettings.defaults()
            );
        }

        public FlowControlSettings(
                boolean enabled,
                @Nullable HeapFlowControlSettings heap
        ) {
            this(enabled, heap, null);
        }

        public FlowControlSettings(
                boolean enabled,
                @Nullable HeapFlowControlSettings heap,
                @Nullable QueueFlowControlSettings queue
        ) {
            this.enabled = enabled;
            this.heap = heap == null ? HeapFlowControlSettings.defaults() : heap;
            this.queue = queue == null ? QueueFlowControlSettings.defaults() : queue;
        }
    }

    /** Destination queue admission limit measured in queued payload bytes. */
    public record QueueFlowControlSettings(
            boolean enabled,
            long maxPendingBytes,
            long resumePendingBytes
    ) {

        private static final long DEFAULT_MAX_PENDING_BYTES = 64L * 1024 * 1024;
        private static final long DEFAULT_RESUME_PENDING_BYTES = 48L * 1024 * 1024;

        public static QueueFlowControlSettings defaults() {
            return new QueueFlowControlSettings(
                    true,
                    DEFAULT_MAX_PENDING_BYTES,
                    DEFAULT_RESUME_PENDING_BYTES
            );
        }

        public QueueFlowControlSettings {
            if (maxPendingBytes <= 0) {
                throw new IllegalArgumentException("Queue max pending bytes must be greater than zero");
            }
            if (resumePendingBytes < 0 || resumePendingBytes >= maxPendingBytes) {
                throw new IllegalArgumentException(
                        "Queue resume pending bytes must be at least zero and lower than max pending bytes"
                );
            }
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
