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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * @author yun
 */
class PerfTestOptionsTest {

    @Test
    void parses_options_supplied_by_go_cli() {
        PerfTestOptions options = PerfTestOptions.parse(new String[]{
                "--host", "localhost",
                "--port", "61613",
                "--destination", "/queue/perf",
                "--warmup-messages", "100",
                "--messages", "1000",
                "--producers", "4",
                "--payload-bytes", "256",
                "--connect-timeout-millis", "2000",
                "--completion-timeout-millis", "30000"
        });

        assertThat(options.host()).isEqualTo("localhost");
        assertThat(options.port()).isEqualTo(61613);
        assertThat(options.destination()).isEqualTo("/queue/perf");
        assertThat(options.warmupMessages()).isEqualTo(100);
        assertThat(options.messages()).isEqualTo(1000);
        assertThat(options.producers()).isEqualTo(4);
        assertThat(options.payloadBytes()).isEqualTo(256);
        assertThat(options.connectTimeout()).isEqualTo(Duration.ofSeconds(2));
        assertThat(options.completionTimeout()).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void rejects_payload_too_small_for_measurement_metadata() {
        String[] arguments = {
                "--host", "localhost",
                "--port", "61613",
                "--destination", "/queue/perf",
                "--warmup-messages", "0",
                "--messages", "1",
                "--producers", "1",
                "--payload-bytes", "12",
                "--connect-timeout-millis", "1000",
                "--completion-timeout-millis", "1000"
        };

        assertThatThrownBy(() -> PerfTestOptions.parse(arguments))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 20");
    }
}
