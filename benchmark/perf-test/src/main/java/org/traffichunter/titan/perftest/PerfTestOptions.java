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
package org.traffichunter.titan.perftest;

import java.time.Duration;

import org.traffichunter.titan.core.util.DestinationGroups;

/**
 * Validated settings supplied by the Go CLI to one performance test run.
 *
 * @author yun
 */
record PerfTestOptions(
        String host,
        int port,
        String group,
        String destination,
        int warmupMessages,
        int messages,
        int producers,
        int payloadBytes,
        Duration connectTimeout,
        Duration completionTimeout
) {

    private static final int MEASUREMENT_BYTES = Long.BYTES + Integer.BYTES + Long.BYTES;

    static PerfTestOptions parse(String[] arguments) {
        String host = null;
        int port = 0;
        String group = null;
        String destination = null;
        int warmupMessages = 0;
        int messages = 0;
        int producers = 0;
        int payloadBytes = 0;
        long connectTimeoutMillis = 0;
        long completionTimeoutMillis = 0;

        for (int index = 0; index < arguments.length; index += 2) {
            if (index + 1 >= arguments.length) {
                throw new IllegalArgumentException("Missing value for " + arguments[index]);
            }
            String value = arguments[index + 1];
            switch (arguments[index]) {
                case "--host" -> host = value;
                case "--port" -> port = positiveInt("port", value);
                case "--group" -> group = value;
                case "--destination" -> destination = value;
                case "--warmup-messages" -> warmupMessages = nonNegativeInt("warmup messages", value);
                case "--messages" -> messages = positiveInt("messages", value);
                case "--producers" -> producers = positiveInt("producers", value);
                case "--payload-bytes" -> payloadBytes = positiveInt("payload bytes", value);
                case "--connect-timeout-millis" -> connectTimeoutMillis = positiveLong("connect timeout", value);
                case "--completion-timeout-millis" -> completionTimeoutMillis = positiveLong("completion timeout", value);
                default -> throw new IllegalArgumentException("Unknown option: " + arguments[index]);
            }
        }

        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("host must not be blank");
        }
        if (port > 65_535) {
            throw new IllegalArgumentException("port must not exceed 65535");
        }
        if (destination == null || destination.isBlank()) {
            throw new IllegalArgumentException("destination must not be blank");
        }
        if (destination.indexOf('\r') >= 0 || destination.indexOf('\n') >= 0 || destination.indexOf(0) >= 0) {
            throw new IllegalArgumentException("destination contains an invalid STOMP character");
        }
        if (payloadBytes < MEASUREMENT_BYTES) {
            throw new IllegalArgumentException("payload bytes must be at least " + MEASUREMENT_BYTES);
        }

        return new PerfTestOptions(
                host,
                port,
                // A missing or blank name is the default group, the same reading the broker gives it.
                DestinationGroups.normalize(group),
                destination,
                warmupMessages,
                messages,
                producers,
                payloadBytes,
                Duration.ofMillis(connectTimeoutMillis),
                Duration.ofMillis(completionTimeoutMillis)
        );
    }

    private static int positiveInt(String name, String value) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed <= 0) {
                throw new IllegalArgumentException(name + " must be greater than zero");
            }
            return parsed;
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Invalid " + name + ": " + value, error);
        }
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
