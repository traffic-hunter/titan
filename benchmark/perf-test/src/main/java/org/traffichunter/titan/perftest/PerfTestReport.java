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

import java.lang.management.ManagementFactory;
import java.util.List;

import org.traffichunter.titan.core.codec.json.Json;
import org.traffichunter.titan.perftest.PerfTestOptions.SendMode;

/**
 * Measurements returned to the Go CLI as a single JSON object.
 *
 * <p>A stage this runner cannot observe is serialized as {@code null} rather than as a zero, so a
 * missing measurement is never read as a successful one. {@code written} is always null here: the
 * channel completes a write once the outbound pipeline ran, which can leave bytes behind in the
 * channel's own buffer, so this runner has no way to see a socket write through.</p>
 *
 * @author yun
 */
record PerfTestReport(
        String runId,
        PerfTestOptions options,
        MeasurementSnapshot snapshot,
        long elapsedNanos,
        boolean completedBeforeDeadline,
        boolean producersStopped,
        List<String> cleanupErrors
) {

    String toJson() {
        MeasurementSnapshot measurements = snapshot;
        boolean receiptMode = options.sendMode() == SendMode.RECEIPT;
        double elapsedSeconds = elapsedNanos / 1_000_000_000.0;
        double throughput = elapsedSeconds == 0 ? 0 : measurements.received() / elapsedSeconds;

        Result result = new Result(
                runId,
                options.sendMode().label(),
                options.transport().label(),
                options.pathLabel().label(),
                options.group(),
                options.destination(),
                options.producers(),
                options.payloadBytes(),
                measurements.requested(),
                measurements.attempted(),
                measurements.notAttempted(),
                receiptMode ? null : measurements.writeSubmitted(),
                null,
                receiptMode ? measurements.accepted() : null,
                measurements.rejected(),
                measurements.localNotSent(),
                measurements.unknown(),
                measurements.received(),
                measurements.duplicates(),
                measurements.acceptedNotReceived(),
                measurements.contradiction(),
                measurements.foreignMessages(),
                measurements.malformedMessages(),
                measurements.warmupRequested(),
                measurements.warmupReceived(),
                measurements.countsBalanced(),
                completedBeforeDeadline,
                producersStopped,
                List.copyOf(cleanupErrors),
                elapsedNanos,
                throughput,
                Latency.of(measurements.deliveryLatencyNanos()),
                receiptMode ? Latency.of(measurements.acceptLatencyNanos()) : null,
                Environment.current()
        );

        return Json.serialize(result);
    }

    /**
     * Latency distribution of one stage.
     *
     * <p>A stage with no samples has no distribution. Missing samples are left out rather than
     * filled in with zeros, which would pull every percentile towards a latency nobody measured.</p>
     */
    record Latency(int samples, long p50Nanos, long p95Nanos, long p99Nanos, long maxNanos) {

        static Latency of(long[] sorted) {
            if (sorted.length == 0) {
                return null;
            }
            return new Latency(
                    sorted.length,
                    percentile(sorted, 0.50),
                    percentile(sorted, 0.95),
                    percentile(sorted, 0.99),
                    sorted[sorted.length - 1]
            );
        }

        private static long percentile(long[] sorted, double percentile) {
            int index = (int) Math.ceil(percentile * sorted.length) - 1;
            return sorted[Math.clamp(index, 0, sorted.length - 1)];
        }
    }

    /** The machine and runtime the load generator ran on. */
    record Environment(
            String javaVersion,
            String javaVendor,
            String jvmName,
            String osName,
            String osVersion,
            String osArch,
            int availableProcessors,
            long maxHeapBytes,
            List<String> jvmArguments
    ) {

        static Environment current() {
            return new Environment(
                    System.getProperty("java.version", "unknown"),
                    System.getProperty("java.vendor", "unknown"),
                    System.getProperty("java.vm.name", "unknown"),
                    System.getProperty("os.name", "unknown"),
                    System.getProperty("os.version", "unknown"),
                    System.getProperty("os.arch", "unknown"),
                    Runtime.getRuntime().availableProcessors(),
                    Runtime.getRuntime().maxMemory(),
                    List.copyOf(ManagementFactory.getRuntimeMXBean().getInputArguments())
            );
        }
    }

    record Result(
            String runId,
            String sendMode,
            String transport,
            String pathLabel,
            String group,
            String destination,
            int producers,
            int payloadBytes,
            int requested,
            int attempted,
            int notAttempted,
            Integer writeSubmitted,
            Integer written,
            Integer accepted,
            int rejected,
            int localNotSent,
            int unknown,
            int received,
            int duplicates,
            int acceptedNotReceived,
            int contradiction,
            int foreignMessages,
            int malformedMessages,
            int warmupRequested,
            int warmupReceived,
            boolean countsBalanced,
            boolean completedBeforeDeadline,
            boolean producersStopped,
            List<String> cleanupErrors,
            long elapsedNanos,
            double throughput,
            Latency deliveryLatency,
            Latency receiptLatency,
            Environment environment
    ) { }
}
