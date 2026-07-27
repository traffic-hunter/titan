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
    void release_previous_buffer_after_reallocation() {
        Buffer buffer = Buffer.alloc(1, 1);
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
        Buffer input = Buffer.alloc("data");
        accumulator.accumulate(input);
        input.release();

        Buffer result = accumulator.readAll();

        assertThat(result.byteBuf().refCnt()).isOne();
        assertThat(result.toString()).isEqualTo("data");

        result.release();
    }
}
