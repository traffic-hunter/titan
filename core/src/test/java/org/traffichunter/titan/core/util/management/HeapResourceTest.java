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
