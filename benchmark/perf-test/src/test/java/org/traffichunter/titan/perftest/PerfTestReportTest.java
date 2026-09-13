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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * @author yun
 */
class PerfTestReportTest {

    @Test
    void serializes_every_stage_separately_for_the_go_cli() {
        String json = report(options("receipt"), snapshot(4, 4, 0, 0, 4, 0, 0, 0, 4, 0, 0, 0)).toJson();

        assertThat(json)
                .contains("\"sendMode\":\"receipt\"")
                .contains("\"transport\":\"tcp\"")
                .contains("\"pathLabel\":\"dispatch\"")
                .contains("\"requested\":4")
                .contains("\"accepted\":4")
                .contains("\"received\":4")
                .contains("\"throughput\":2.0")
                .contains("\"countsBalanced\":true")
                .contains("\"deliveryLatency\":{\"samples\":4,\"p50Nanos\":2000000")
                .contains("\"receiptLatency\":{\"samples\":4");
    }

    @Test
    void a_stage_this_runner_cannot_observe_is_null_rather_than_zero() {
        String receiptRun = report(options("receipt"), snapshot(4, 4, 0, 0, 4, 0, 0, 0, 4, 0, 0, 0)).toJson();
        String writeRun = report(options("write"), snapshot(4, 4, 0, 4, 0, 0, 0, 0, 4, 0, 0, 0)).toJson();

        // A socket write is never seen through here, so it is reported as unsupported in both modes.
        assertThat(receiptRun).contains("\"written\":null").contains("\"writeSubmitted\":null");
        assertThat(writeRun).contains("\"written\":null").contains("\"accepted\":null");
        // The write mode makes no claim about acceptance, so it carries no receipt latency either.
        assertThat(writeRun).contains("\"receiptLatency\":null").contains("\"writeSubmitted\":4");
    }

    @Test
    void a_stage_with_no_samples_has_no_percentiles_at_all() {
        MeasurementSnapshot missed = new MeasurementSnapshot(
                4, 4, 0, 0, 4, 0, 0, 0, 0, 0, 4, 0, 0, 0, 0, 0, new long[0], new long[0]
        );

        String json = report(options("receipt"), missed).toJson();

        // Filling the gap with zeros would report a run that lost everything as the fastest one.
        assertThat(json).contains("\"deliveryLatency\":null").contains("\"receiptLatency\":null");
        assertThat(json).contains("\"acceptedNotReceived\":4").contains("\"throughput\":0.0");
    }

    @Test
    void an_unfinished_run_says_so_instead_of_hiding_it_in_the_totals() {
        MeasurementSnapshot partial = new MeasurementSnapshot(
                4, 3, 1, 0, 2, 0, 0, 1, 2, 1, 0, 1, 0, 0, 0, 0, new long[]{1_000}, new long[]{2_000}
        );

        String json = new PerfTestReport(
                "0000000000000001",
                options("receipt"),
                partial,
                2_000_000_000L,
                false,
                false,
                List.of("Failed to shut down the producer connection: boom")
        ).toJson();

        assertThat(json)
                .contains("\"notAttempted\":1")
                .contains("\"unknown\":1")
                .contains("\"duplicates\":1")
                .contains("\"contradiction\":1")
                .contains("\"completedBeforeDeadline\":false")
                .contains("\"producersStopped\":false")
                .contains("\"cleanupErrors\":[\"Failed to shut down the producer connection: boom\"]");
    }

    private static PerfTestReport report(PerfTestOptions options, MeasurementSnapshot snapshot) {
        return new PerfTestReport(
                "0000000000000001",
                options,
                snapshot,
                2_000_000_000L,
                true,
                true,
                List.of()
        );
    }

    private static PerfTestOptions options(String sendMode) {
        return PerfTestOptions.parse(new String[]{
                "--host", "localhost",
                "--port", "61613",
                "--send-mode", sendMode,
                "--destination", "/queue/perf",
                "--warmup-messages", "0",
                "--messages", "4",
                "--producers", "1",
                "--payload-bytes", "256",
                "--connect-timeout-millis", "2000",
                "--completion-timeout-millis", "120000"
        });
    }

    private static MeasurementSnapshot snapshot(
            int requested,
            int attempted,
            int notAttempted,
            int writeSubmitted,
            int accepted,
            int rejected,
            int localNotSent,
            int unknown,
            int received,
            int duplicates,
            int acceptedNotReceived,
            int contradiction
    ) {
        long[] latencies = {1_000_000, 2_000_000, 3_000_000, 4_000_000};
        return new MeasurementSnapshot(
                requested,
                attempted,
                notAttempted,
                writeSubmitted,
                accepted,
                rejected,
                localNotSent,
                unknown,
                received,
                duplicates,
                acceptedNotReceived,
                contradiction,
                0,
                0,
                0,
                0,
                latencies,
                latencies
        );
    }
}
