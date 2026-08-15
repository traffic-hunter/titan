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
package org.traffichunter.titan.core.channel;

import org.junit.jupiter.api.Test;
import org.traffichunter.titan.core.util.buffer.Buffer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author yun
 */
class ChannelWriteBufferTest {

    @Test
    void attach_existing_buffer_state_to_event_loop_group_metrics() {
        ChannelWriteBuffer writeBuffer = new ChannelWriteBuffer(8, 4);
        writeBuffer.add(Buffer.heap().alloc(new byte[9]));

        AggregateChannelWriteBufferMetrics metrics = new AggregateChannelWriteBufferMetrics();
        writeBuffer.attachMetrics(metrics);

        assertThat(metrics.getActiveBuffers()).isOne();
        assertThat(metrics.getPendingBytes()).isEqualTo(9);
        assertThat(metrics.getNonWritableBuffers()).isOne();

        writeBuffer.close();
    }

    @Test
    void expose_pending_bytes_and_watermark_state() {
        AggregateChannelWriteBufferMetrics metrics = new AggregateChannelWriteBufferMetrics();
        ChannelWriteBuffer writeBuffer = new ChannelWriteBuffer(8, 4, metrics);
        Buffer first = Buffer.heap().alloc(new byte[5]);
        Buffer second = Buffer.heap().alloc(new byte[4]);

        writeBuffer.add(first);
        writeBuffer.add(second);

        assertThat(writeBuffer.pendingBytes()).isEqualTo(9);
        assertThat(writeBuffer.highWatermark()).isEqualTo(8);
        assertThat(writeBuffer.lowWatermark()).isEqualTo(4);
        assertThat(writeBuffer.isWritable()).isFalse();
        assertThat(metrics.getActiveBuffers()).isOne();
        assertThat(metrics.getPendingBytes()).isEqualTo(9);
        assertThat(metrics.getNonWritableBuffers()).isOne();

        writeBuffer.close();
    }

    @Test
    void reduce_pending_bytes_as_socket_write_progresses() {
        AggregateChannelWriteBufferMetrics metrics = new AggregateChannelWriteBufferMetrics();
        ChannelWriteBuffer writeBuffer = new ChannelWriteBuffer(8, 4, metrics);
        Buffer payload = Buffer.heap().alloc(new byte[9]);
        writeBuffer.add(payload);

        payload.skipBytes(6);
        writeBuffer.progress(6);

        assertThat(writeBuffer.pendingBytes()).isEqualTo(3);
        assertThat(writeBuffer.isWritable()).isTrue();
        assertThat(metrics.getPendingBytes()).isEqualTo(3);
        assertThat(metrics.getNonWritableBuffers()).isZero();

        Buffer remaining = writeBuffer.poll();
        assertThat(remaining).isSameAs(payload);
        assertThat(writeBuffer.pendingBytes()).isZero();
        remaining.release();
        writeBuffer.close();
    }

    @Test
    void close_releases_remaining_metrics_once() {
        AggregateChannelWriteBufferMetrics metrics = new AggregateChannelWriteBufferMetrics();
        ChannelWriteBuffer writeBuffer = new ChannelWriteBuffer(8, 4, metrics);
        writeBuffer.add(Buffer.heap().alloc(new byte[9]));

        writeBuffer.close();
        writeBuffer.close();

        assertThat(metrics.getActiveBuffers()).isZero();
        assertThat(metrics.getPendingBytes()).isZero();
        assertThat(metrics.getNonWritableBuffers()).isZero();
    }
}
