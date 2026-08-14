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
package org.traffichunter.titan.core.resilience.flowcontrol;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.traffichunter.titan.core.util.management.HeapResource;

class MemoryPressureDamperTest {

    @Test
    void closes_at_high_watermark_and_reopens_at_low_watermark() {
        AtomicReference<HeapResource> heap = new AtomicReference<>(heapUsage(0.50));
        MemoryPressureDamper damper = new MemoryPressureDamper(
                heap::get,
                new FlowControlConfiguration(0.80, 0.60)
        );

        assertThat(damper.regulate()).isEqualTo(DamperStatus.OPEN);

        heap.set(heapUsage(0.85));
        assertThat(damper.regulate()).isEqualTo(DamperStatus.CLOSED);

        heap.set(heapUsage(0.70));
        assertThat(damper.regulate()).isEqualTo(DamperStatus.CLOSED);

        heap.set(heapUsage(0.60));
        assertThat(damper.regulate()).isEqualTo(DamperStatus.OPEN);
    }

    private static HeapResource heapUsage(double usage) {
        long max = 1_000;
        return new HeapResource(0, (long) (max * usage), max, max);
    }
}
