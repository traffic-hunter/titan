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
package org.traffichunter.titan.core.util.management;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author yun
 */
class HeapResourceTest {

    @Test
    void calculate_usage_against_maximum_heap() {
        HeapResource heap = new HeapResource(100, 750, 800, 1_000);

        assertThat(heap.limit()).isEqualTo(1_000);
        assertThat(heap.usage()).isEqualTo(0.75);
    }

    @Test
    void use_committed_heap_when_maximum_is_undefined() {
        HeapResource heap = new HeapResource(100, 600, 800, -1);

        assertThat(heap.limit()).isEqualTo(800);
        assertThat(heap.usage()).isEqualTo(0.75);
    }

    @Test
    void return_zero_usage_when_effective_limit_is_unavailable() {
        HeapResource heap = new HeapResource(-1, 100, -1, -1);

        assertThat(heap.usage()).isZero();
    }

    @Test
    void cap_usage_when_reported_used_heap_exceeds_limit() {
        HeapResource heap = new HeapResource(100, 1_100, 1_000, 1_000);

        assertThat(heap.usage()).isEqualTo(1.0);
    }
}
