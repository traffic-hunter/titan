/*
The MIT License

Copyright (c) 2025 traffic-hunter

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
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
