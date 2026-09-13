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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.traffichunter.titan.stability.StabilityFixtureOptions.DeliveryPath;
import org.traffichunter.titan.stability.StabilityFixtureOptions.DispatchModeName;
import org.traffichunter.titan.stability.StabilityFixtureOptions.Transport;

/**
 * @author yun
 */
class StabilityFixtureOptionsTest {

    @Test
    void a_fixture_serves_the_dispatch_path_over_tcp_on_a_port_the_os_picks() {
        StabilityFixtureOptions options = StabilityFixtureOptions.parse(new String[0]);

        assertThat(options.port()).isZero();
        assertThat(options.transport()).isEqualTo(Transport.TCP);
        assertThat(options.path()).isEqualTo(DeliveryPath.DISPATCH);
        assertThat(options.dispatchMode()).isEqualTo(DispatchModeName.VIRTUAL);
    }

    @Test
    void the_queue_limits_are_named_rather_than_inherited() {
        StabilityFixtureOptions options = StabilityFixtureOptions.parse(new String[0]);

        // The shipped configuration gates these behind a flow-control switch that is off, so the
        // fixture states them itself instead of measuring an unbounded queue by accident.
        assertThat(options.queueMaxPendingBytes()).isEqualTo(64L * 1024 * 1024);
        assertThat(options.queueResumePendingBytes()).isEqualTo(48L * 1024 * 1024);
    }

    @Test
    void reads_every_choice_a_run_depends_on() {
        StabilityFixtureOptions options = StabilityFixtureOptions.parse(new String[]{
                "--host", "0.0.0.0",
                "--port", "7777",
                "--transport", "websocket",
                "--websocket-path", "titan",
                "--path", "direct",
                "--dispatch-mode", "platform",
                "--io-workers", "4",
                "--max-frame-length", "2048",
                "--queue-max-pending-bytes", "1024",
                "--queue-resume-pending-bytes", "512",
                "--manifest", "/tmp/fixture.json"
        });

        assertThat(options.host()).isEqualTo("0.0.0.0");
        assertThat(options.port()).isEqualTo(7777);
        assertThat(options.transport()).isEqualTo(Transport.WEBSOCKET);
        assertThat(options.webSocketPath()).isEqualTo("/titan");
        assertThat(options.path()).isEqualTo(DeliveryPath.DIRECT);
        assertThat(options.dispatchMode()).isEqualTo(DispatchModeName.PLATFORM);
        assertThat(options.ioWorkers()).isEqualTo(4);
        assertThat(options.maxFrameLength()).isEqualTo(2048);
        assertThat(options.queueMaxPendingBytes()).isEqualTo(1024);
        assertThat(options.queueResumePendingBytes()).isEqualTo(512);
        assertThat(options.manifestPath()).isEqualTo("/tmp/fixture.json");
    }

    @Test
    void rejects_a_resume_threshold_the_queue_could_never_fall_back_to() {
        assertThatThrownBy(() -> StabilityFixtureOptions.parse(new String[]{
                "--queue-max-pending-bytes", "1024",
                "--queue-resume-pending-bytes", "1024"
        }))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("below the queue maximum");
    }

    @Test
    void rejects_a_setting_it_cannot_honour() {
        assertThatThrownBy(() -> StabilityFixtureOptions.parse(new String[]{"--transport", "udp"}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid transport");
        assertThatThrownBy(() -> StabilityFixtureOptions.parse(new String[]{"--path", "queue"}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid path");
        assertThatThrownBy(() -> StabilityFixtureOptions.parse(new String[]{"--unknown", "1"}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown option");
    }
}
