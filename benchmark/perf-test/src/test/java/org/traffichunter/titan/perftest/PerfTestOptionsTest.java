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
