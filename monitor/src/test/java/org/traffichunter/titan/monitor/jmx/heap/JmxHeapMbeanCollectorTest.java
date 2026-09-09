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
package org.traffichunter.titan.monitor.jmx.heap;

import org.junit.jupiter.api.Test;
import org.traffichunter.titan.core.util.management.HeapResource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author yun
 */
class JmxHeapMbeanCollectorTest {

    @Test
    void collect_heap_data_from_shared_resource_detector() {
        JmxHeapMbeanCollector collector = new JmxHeapMbeanCollector(
                () -> new HeapResource(100, 400, 800, 1_000)
        );

        HeapData heap = collector.collect();

        assertThat(heap).isEqualTo(new HeapData(100, 400, 800, 1_000));
    }
}
