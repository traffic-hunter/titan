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
