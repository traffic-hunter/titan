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
package org.traffichunter.titan.stability;

import java.util.Locale;

/**
 * Settings of one stability fixture server.
 *
 * <p>Every choice a stability run depends on is named here rather than inherited from a
 * configuration file. The queue limits in particular are applied unconditionally: the shipped
 * configuration gates them behind a flow-control switch that is off by default, so a run that read
 * its limits from {@code titan-env.yml} would be measuring an unbounded queue while the file said
 * otherwise.</p>
 *
 * @author yun
 */
public record StabilityFixtureOptions(
        String host,
        int port,
        Transport transport,
        String webSocketPath,
        DeliveryPath path,
        DispatchModeName dispatchMode,
        int ioWorkers,
        int maxFrameLength,
        long queueMaxPendingBytes,
        long queueResumePendingBytes,
        String manifestPath
) {

    public static final long DEFAULT_QUEUE_MAX_PENDING_BYTES = 64L * 1024 * 1024;
    public static final long DEFAULT_QUEUE_RESUME_PENDING_BYTES = 48L * 1024 * 1024;

    private static final String DEFAULT_WEBSOCKET_PATH = "/stomp";
    private static final int DEFAULT_MAX_FRAME_LENGTH = 1024 * 1024;

    /** How accepted STOMP sessions reach the fixture. */
    public enum Transport {
        TCP,
        WEBSOCKET;

        public static Transport parse(String value) {
            return switch (value.toLowerCase(Locale.ROOT).trim()) {
                case "tcp" -> TCP;
                case "websocket", "ws" -> WEBSOCKET;
                default -> throw new IllegalArgumentException("Invalid transport: " + value);
            };
        }

        public String label() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Which server path carries SEND frames to subscribers. */
    public enum DeliveryPath {
        /** The dispatch queue path, where the 0.9.0 retention contract applies. */
        DISPATCH,
        /** The built-in best-effort STOMP fanout, kept only for comparison. */
        DIRECT;

        public static DeliveryPath parse(String value) {
            return switch (value.toLowerCase(Locale.ROOT).trim()) {
                case "dispatch" -> DISPATCH;
                case "direct" -> DIRECT;
                default -> throw new IllegalArgumentException("Invalid path: " + value);
            };
        }

        public String label() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Which executor the dispatch gateway runs on. */
    public enum DispatchModeName {
        PLATFORM("platform"),
        VIRTUAL("virtual");

        private final String label;

        DispatchModeName(String label) {
            this.label = label;
        }

        public static DispatchModeName parse(String value) {
            return switch (value.toLowerCase(Locale.ROOT).trim()) {
                case "platform", "fixed" -> PLATFORM;
                case "virtual" -> VIRTUAL;
                default -> throw new IllegalArgumentException("Invalid dispatch mode: " + value);
            };
        }

        public String label() {
            return label;
        }
    }

    public static StabilityFixtureOptions parse(String[] arguments) {
        String host = "127.0.0.1";
        int port = 0;
        Transport transport = Transport.TCP;
        String webSocketPath = DEFAULT_WEBSOCKET_PATH;
        DeliveryPath path = DeliveryPath.DISPATCH;
        DispatchModeName dispatchMode = DispatchModeName.VIRTUAL;
        int ioWorkers = Runtime.getRuntime().availableProcessors();
        int maxFrameLength = DEFAULT_MAX_FRAME_LENGTH;
        long queueMaxPendingBytes = DEFAULT_QUEUE_MAX_PENDING_BYTES;
        long queueResumePendingBytes = DEFAULT_QUEUE_RESUME_PENDING_BYTES;
        String manifestPath = "";

        for (int index = 0; index < arguments.length; index += 2) {
            if (index + 1 >= arguments.length) {
                throw new IllegalArgumentException("Missing value for " + arguments[index]);
            }
            String value = arguments[index + 1];
            switch (arguments[index]) {
                case "--host" -> host = value;
                case "--port" -> port = nonNegativeInt("port", value);
                case "--transport" -> transport = Transport.parse(value);
                case "--websocket-path" -> webSocketPath = value;
                case "--path" -> path = DeliveryPath.parse(value);
                case "--dispatch-mode" -> dispatchMode = DispatchModeName.parse(value);
                case "--io-workers" -> ioWorkers = positiveInt("io workers", value);
                case "--max-frame-length" -> maxFrameLength = positiveInt("max frame length", value);
                case "--queue-max-pending-bytes" -> queueMaxPendingBytes = positiveLong("queue max pending bytes", value);
                case "--queue-resume-pending-bytes" ->
                        queueResumePendingBytes = positiveLong("queue resume pending bytes", value);
                case "--manifest" -> manifestPath = value;
                default -> throw new IllegalArgumentException("Unknown option: " + arguments[index]);
            }
        }

        if (host.isBlank()) {
            throw new IllegalArgumentException("host must not be blank");
        }
        if (port > 65_535) {
            throw new IllegalArgumentException("port must not exceed 65535");
        }
        if (queueResumePendingBytes >= queueMaxPendingBytes) {
            throw new IllegalArgumentException("queue resume pending bytes must be below the queue maximum");
        }

        return new StabilityFixtureOptions(
                host,
                port,
                transport,
                normalizeWebSocketPath(webSocketPath),
                path,
                dispatchMode,
                ioWorkers,
                maxFrameLength,
                queueMaxPendingBytes,
                queueResumePendingBytes,
                manifestPath
        );
    }

    private static String normalizeWebSocketPath(String path) {
        if (path.isBlank()) {
            return "/";
        }
        return path.startsWith("/") ? path : "/" + path;
    }

    private static int positiveInt(String name, String value) {
        int parsed = nonNegativeInt(name, value);
        if (parsed == 0) {
            throw new IllegalArgumentException(name + " must be greater than zero");
        }
        return parsed;
    }

    private static int nonNegativeInt(String name, String value) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 0) {
                throw new IllegalArgumentException(name + " must not be negative");
            }
            return parsed;
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Invalid " + name + ": " + value, error);
        }
    }

    private static long positiveLong(String name, String value) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed <= 0) {
                throw new IllegalArgumentException(name + " must be greater than zero");
            }
            return parsed;
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Invalid " + name + ": " + value, error);
        }
    }
}
