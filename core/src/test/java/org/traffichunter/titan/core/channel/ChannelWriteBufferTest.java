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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author yun
 */
class ChannelWriteBufferTest {

    @Test
    void attach_existing_buffer_state_to_event_loop_group_metrics() {
        ChannelWriteBuffer writeBuffer = new ChannelWriteBuffer(8, 4);
        writeBuffer.append(Buffer.heap().alloc(new byte[9]));

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
        ChannelWriteBuffer writeBuffer = new ChannelWriteBuffer(Integer.MAX_VALUE, 8, 4, metrics);
        Buffer first = Buffer.heap().alloc(new byte[5]);
        Buffer second = Buffer.heap().alloc(new byte[4]);

        writeBuffer.append(first);
        writeBuffer.append(second);

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
        ChannelWriteBuffer writeBuffer = new ChannelWriteBuffer(Integer.MAX_VALUE, 8, 4, metrics);
        Buffer payload = Buffer.heap().alloc(new byte[9]);
        writeBuffer.append(payload);

        writeBuffer.consume(6);

        assertThat(payload.length()).isEqualTo(3);
        assertThat(payload.byteBuf().readerIndex()).isEqualTo(6);
        assertThat(writeBuffer.current()).isSameAs(payload);
        assertThat(writeBuffer.pendingBytes()).isEqualTo(3);
        assertThat(writeBuffer.isWritable()).isTrue();
        assertThat(metrics.getPendingBytes()).isEqualTo(3);
        assertThat(metrics.getNonWritableBuffers()).isZero();

        writeBuffer.consume(3);
        assertThat(writeBuffer.current()).isNull();
        assertThat(writeBuffer.isEmpty()).isTrue();
        assertThat(writeBuffer.pendingBytes()).isZero();
        assertThat(metrics.getPendingBytes()).isZero();
        assertThat(payload.byteBuf().refCnt()).isZero();
        writeBuffer.close();
    }

    @Test
    void close_releases_remaining_metrics_once() {
        AggregateChannelWriteBufferMetrics metrics = new AggregateChannelWriteBufferMetrics();
        ChannelWriteBuffer writeBuffer = new ChannelWriteBuffer(Integer.MAX_VALUE, 8, 4, metrics);
        writeBuffer.append(Buffer.heap().alloc(new byte[9]));

        writeBuffer.close();
        writeBuffer.close();

        assertThat(metrics.getActiveBuffers()).isZero();
        assertThat(metrics.getPendingBytes()).isZero();
        assertThat(metrics.getNonWritableBuffers()).isZero();
    }

    @Test
    void consume_releases_only_the_completed_buffer() {
        AggregateChannelWriteBufferMetrics metrics = new AggregateChannelWriteBufferMetrics();
        ChannelWriteBuffer writeBuffer = new ChannelWriteBuffer(Integer.MAX_VALUE, 8, 4, metrics);
        Buffer first = Buffer.direct().alloc(new byte[5]);
        Buffer second = Buffer.direct().alloc(new byte[4]);
        try {
            writeBuffer.append(first);
            writeBuffer.append(second);

            writeBuffer.consume(5);

            assertThat(first.byteBuf().refCnt()).isZero();
            assertThat(writeBuffer.current()).isSameAs(second);
            assertThat(second.length()).isEqualTo(4);
            assertThat(second.byteBuf().refCnt()).isOne();
            assertThat(writeBuffer.pendingBytes()).isEqualTo(4);
            assertThat(metrics.getPendingBytes()).isEqualTo(4);
            assertThat(writeBuffer.isWritable()).isFalse();

            writeBuffer.consume(1);
            assertThat(writeBuffer.pendingBytes()).isEqualTo(3);
            assertThat(writeBuffer.isWritable()).isTrue();
            assertThat(metrics.getNonWritableBuffers()).isZero();
        } finally {
            writeBuffer.close();
        }
        assertThat(second.byteBuf().refCnt()).isZero();
        assertThat(metrics.getPendingBytes()).isZero();
    }

    @Test
    void invalid_consume_does_not_change_buffers_or_metrics() {
        AggregateChannelWriteBufferMetrics metrics = new AggregateChannelWriteBufferMetrics();
        ChannelWriteBuffer writeBuffer = new ChannelWriteBuffer(Integer.MAX_VALUE, 8, 4, metrics);
        Buffer first = Buffer.direct().alloc(new byte[5]);
        Buffer second = Buffer.direct().alloc(new byte[4]);
        try {
            writeBuffer.append(first);
            writeBuffer.append(second);

            assertThatThrownBy(() -> writeBuffer.consume(-1)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> writeBuffer.consume(6)).isInstanceOf(IllegalArgumentException.class);
            writeBuffer.consume(0);

            assertThat(writeBuffer.current()).isSameAs(first);
            assertThat(first.length()).isEqualTo(5);
            assertThat(first.byteBuf().refCnt()).isOne();
            assertThat(second.length()).isEqualTo(4);
            assertThat(writeBuffer.pendingBytes()).isEqualTo(9);
            assertThat(metrics.getPendingBytes()).isEqualTo(9);
            assertThat(metrics.getNonWritableBuffers()).isOne();
        } finally {
            writeBuffer.close();
        }
    }

    @Test
    void consume_handles_empty_and_closed_buffers() {
        ChannelWriteBuffer writeBuffer = new ChannelWriteBuffer();
        try {
            writeBuffer.consume(0);
            assertThatThrownBy(() -> writeBuffer.consume(1)).isInstanceOf(IllegalArgumentException.class);
            assertThat(writeBuffer.pendingBytes()).isZero();
            assertThat(writeBuffer.isEmpty()).isTrue();
        } finally {
            writeBuffer.close();
        }
        assertThatThrownBy(() -> writeBuffer.consume(0)).isInstanceOf(ChannelException.class);
    }
}
