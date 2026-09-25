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

import static org.assertj.core.api.Assertions.assertThat;

import java.net.Socket;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * @author yun
 */
class StabilityFixtureTest {

    @Test
    @Timeout(60)
    void a_started_fixture_reports_the_port_it_bound_and_the_limits_it_applied() throws Exception {
        StabilityFixtureOptions options = StabilityFixtureOptions.parse(new String[]{
                "--path", "dispatch",
                "--queue-max-pending-bytes", "1048576",
                "--queue-resume-pending-bytes", "524288"
        });

        try (StabilityFixture fixture = StabilityFixture.start(options)) {
            FixtureManifest manifest = fixture.manifest();

            assertThat(fixture.port()).isGreaterThan(0);
            assertThat(manifest.port()).isEqualTo(fixture.port());
            assertThat(manifest.path()).isEqualTo("dispatch");
            assertThat(manifest.queueMaxPendingBytes()).isEqualTo(1_048_576);
            assertThat(manifest.queueResumePendingBytes()).isEqualTo(524_288);
            assertThat(manifest.queueLimitsApplied()).isTrue();
            assertThat(manifest.toJson()).contains("\"queueLimitsApplied\":true");

            try (Socket socket = new Socket("127.0.0.1", fixture.port())) {
                assertThat(socket.isConnected()).isTrue();
            }
        }
    }

    @Test
    @Timeout(60)
    void a_direct_fixture_claims_no_queue_limits_because_it_has_no_queues() throws Exception {
        StabilityFixtureOptions options = StabilityFixtureOptions.parse(new String[]{"--path", "direct"});

        try (StabilityFixture fixture = StabilityFixture.start(options)) {
            FixtureManifest manifest = fixture.manifest();

            // The direct path never builds a queue, so reporting a limit here would describe a
            // control that is not in the run at all.
            assertThat(manifest.queueLimitsApplied()).isFalse();
            assertThat(manifest.queueMaxPendingBytes()).isZero();
        }
    }
}
