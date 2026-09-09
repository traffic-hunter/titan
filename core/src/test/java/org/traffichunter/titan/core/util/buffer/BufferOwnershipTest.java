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
package org.traffichunter.titan.core.util.buffer;

import static org.assertj.core.api.Assertions.assertThat;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

/**
 * @author yun
 */
class BufferOwnershipTest {

    @Test
    void allocate_application_buffer_on_heap() {
        Buffer buffer = Buffer.heap().alloc("data");

        assertThat(buffer.byteBuf().isDirect()).isFalse();

        buffer.release();
    }

    @Test
    void allocate_transport_buffer_in_direct_memory() {
        Buffer buffer = Buffer.direct().alloc("data");

        assertThat(buffer.byteBuf().isDirect()).isTrue();

        buffer.release();
    }

    @Test
    void preserve_memory_type_after_reallocation() {
        Buffer heap = Buffer.heap().alloc(1, 1);
        Buffer direct = Buffer.direct().alloc(1, 1);

        heap.accumulateString("12");
        direct.accumulateString("12");

        assertThat(heap.byteBuf().isDirect()).isFalse();
        assertThat(direct.byteBuf().isDirect()).isTrue();

        heap.release();
        direct.release();
    }

    @Test
    void release_previous_buffer_after_reallocation() {
        Buffer buffer = Buffer.heap().alloc(1, 1);
        ByteBuf previous = buffer.byteBuf();

        buffer.accumulateString("12");

        assertThat(previous.refCnt()).isZero();
        assertThat(buffer.toString()).isEqualTo("12");

        buffer.release();
    }

    @Test
    void copy_read_only_buffer_into_independent_storage() {
        Buffer source = Buffer.buffer(Unpooled.wrappedBuffer(new byte[] {1, 2}).asReadOnly());
        Buffer copy = source.copy();

        source.release();

        assertThat(copy.byteBuf().refCnt()).isOne();
        assertThat(copy.getBytes()).containsExactly((byte) 1, (byte) 2);

        copy.release();
    }

    @SuppressWarnings("removal")
    @Test
    void keep_read_all_result_alive_after_accumulator_is_cleared() {
        BufferAccumulator accumulator = new BufferAccumulator();
        Buffer input = Buffer.heap().alloc("data");
        accumulator.accumulate(input);
        input.release();

        Buffer result = accumulator.readAll();

        assertThat(result.byteBuf().refCnt()).isOne();
        assertThat(result.toString()).isEqualTo("data");

        result.release();
    }
}
