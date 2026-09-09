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

import org.traffichunter.titan.core.codec.json.Json;

import java.util.Arrays;
import java.util.Locale;

/**
 * Measurements returned to the Go CLI as a single JSON object.
 *
 * @author yun
 */
record PerfTestReport(
        int requested,
        int sent,
        int received,
        int failed,
        long elapsedNanos,
        long[] latencyNanos
) {

    String toJson() {
        long[] samples = Arrays.stream(latencyNanos).filter(value -> value > 0).sorted().toArray();
        double elapsedSeconds = elapsedNanos / 1_000_000_000.0;
        double throughput = elapsedSeconds == 0 ? 0 : received / elapsedSeconds;

        Result result = new Result(
                requested,
                sent,
                received,
                failed,
                elapsedNanos,
                throughput,
                percentile(samples, 0.50),
                percentile(samples, 0.95),
                percentile(samples, 0.99)
        );

        return Json.serialize(result);
    }

    private static long percentile(long[] values, double percentile) {
        if (values.length == 0) {
            return 0;
        }
        int index = (int) Math.ceil(percentile * values.length) - 1;
        return values[Math.max(0, index)];
    }

    record Result(
            int requested,
            int sent,
            int received,
            int failed,
            long elapsedNanos,
            double throughput,
            long latencyP50Nanos,
            long latencyP95Nanos,
            long latencyP99Nanos
    ) { }
}
