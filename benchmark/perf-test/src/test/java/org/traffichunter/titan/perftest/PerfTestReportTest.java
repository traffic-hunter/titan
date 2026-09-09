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

import org.junit.jupiter.api.Test;

/**
 * @author yun
 */
class PerfTestReportTest {

    @Test
    void serializes_summary_for_go_cli() {
        String json = new PerfTestReport(
                4,
                4,
                4,
                0,
                2_000_000_000L,
                new long[]{4_000_000, 1_000_000, 3_000_000, 2_000_000}
        ).toJson();

        assertThat(json)
                .contains("\"throughput\":2.0")
                .contains("\"latencyP50Nanos\":2000000")
                .contains("\"latencyP99Nanos\":4000000");
    }
}
