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

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.traffichunter.titan.stability.StabilityFixture;
import org.traffichunter.titan.stability.StabilityFixtureOptions;

/**
 * Runs the measurement tooling against a real fixture over both transports.
 *
 * <p>These runs are small on purpose. They prove that the options reach the wire, that publishing
 * waits for the subscription's RECEIPT, and that the ledger adds up against a live broker; the load
 * matrix itself belongs to a measured run, not to the test suite.</p>
 *
 * @author yun
 */
class StabilityRunIntegrationTest {

    private static final int MESSAGES = 200;
    private static final int WARMUP_MESSAGES = 20;

    private final List<StabilityFixture> fixtures = new ArrayList<>();

    @AfterEach
    void stopFixtures() {
        fixtures.forEach(StabilityFixture::close);
        fixtures.clear();
    }

    @Test
    @Timeout(120)
    void a_dispatch_run_over_tcp_accounts_for_every_message() throws Exception {
        StabilityFixture fixture = start("--path", "dispatch", "--dispatch-mode", "platform");

        PerfTestReport report = new StompPerfTest()
                .run(options(fixture.port(), "--send-mode", "receipt", "--path-label", "dispatch"));

        assertCleanRun(report);
        assertThat(report.snapshot().accepted()).isEqualTo(MESSAGES);
        assertThat(report.snapshot().writeSubmitted()).isZero();
        assertThat(report.snapshot().warmupReceived()).isEqualTo(WARMUP_MESSAGES);
        assertThat(report.toJson()).contains("\"receiptLatency\":{").contains("\"writeSubmitted\":null");
    }

    @Test
    @Timeout(120)
    void a_dispatch_run_over_websocket_accounts_for_every_message() throws Exception {
        StabilityFixture fixture = start(
                "--path", "dispatch",
                "--dispatch-mode", "virtual",
                "--transport", "websocket",
                "--websocket-path", "/stomp"
        );

        PerfTestReport report = new StompPerfTest().run(options(
                fixture.port(),
                "--transport", "websocket",
                "--websocket-path", "/stomp",
                "--send-mode", "receipt",
                "--path-label", "dispatch"
        ));

        assertCleanRun(report);
        assertThat(report.snapshot().accepted()).isEqualTo(MESSAGES);
        assertThat(report.toJson()).contains("\"transport\":\"websocket\"");
    }

    @Test
    @Timeout(120)
    void a_direct_run_is_reported_as_the_direct_path() throws Exception {
        StabilityFixture fixture = start("--path", "direct");

        PerfTestReport report = new StompPerfTest()
                .run(options(fixture.port(), "--send-mode", "receipt", "--path-label", "direct"));

        assertCleanRun(report);
        // The direct path makes no retention promise, so its result must never be filed as one.
        assertThat(report.toJson()).contains("\"pathLabel\":\"direct\"");
    }

    @Test
    @Timeout(120)
    void the_write_send_mode_claims_nothing_about_acceptance() throws Exception {
        StabilityFixture fixture = start("--path", "dispatch", "--dispatch-mode", "platform");

        PerfTestReport report = new StompPerfTest()
                .run(options(fixture.port(), "--send-mode", "write", "--path-label", "dispatch"));

        assertThat(report.snapshot().writeSubmitted()).isEqualTo(MESSAGES);
        assertThat(report.snapshot().accepted()).isZero();
        assertThat(report.snapshot().received()).isEqualTo(MESSAGES);
        assertThat(report.snapshot().countsBalanced()).isTrue();
        assertThat(report.toJson()).contains("\"accepted\":null").contains("\"receiptLatency\":null");
    }

    private static void assertCleanRun(PerfTestReport report) {
        MeasurementSnapshot snapshot = report.snapshot();
        assertThat(snapshot.requested()).isEqualTo(MESSAGES);
        assertThat(snapshot.attempted()).isEqualTo(MESSAGES);
        assertThat(snapshot.notAttempted()).isZero();
        assertThat(snapshot.received()).isEqualTo(MESSAGES);
        assertThat(snapshot.duplicates()).isZero();
        assertThat(snapshot.rejected()).isZero();
        assertThat(snapshot.localNotSent()).isZero();
        assertThat(snapshot.unknown()).isZero();
        assertThat(snapshot.acceptedNotReceived()).isZero();
        assertThat(snapshot.contradiction()).isZero();
        assertThat(snapshot.foreignMessages()).isZero();
        assertThat(snapshot.malformedMessages()).isZero();
        assertThat(snapshot.countsBalanced()).isTrue();
        assertThat(report.completedBeforeDeadline()).isTrue();
        assertThat(report.producersStopped()).isTrue();
        assertThat(report.cleanupErrors()).isEmpty();
    }

    private StabilityFixture start(String... arguments) throws Exception {
        StabilityFixture fixture = StabilityFixture.start(StabilityFixtureOptions.parse(arguments));
        fixtures.add(fixture);
        return fixture;
    }

    private static PerfTestOptions options(int port, String... extra) {
        List<String> arguments = new ArrayList<>(List.of(
                "--host", "127.0.0.1",
                "--port", String.valueOf(port),
                "--destination", "/queue/stability",
                "--warmup-messages", String.valueOf(WARMUP_MESSAGES),
                "--messages", String.valueOf(MESSAGES),
                "--producers", "2",
                "--payload-bytes", "256",
                "--connect-timeout-millis", "10000",
                "--completion-timeout-millis", "60000"
        ));
        arguments.addAll(List.of(extra));
        return PerfTestOptions.parse(arguments.toArray(new String[0]));
    }
}
