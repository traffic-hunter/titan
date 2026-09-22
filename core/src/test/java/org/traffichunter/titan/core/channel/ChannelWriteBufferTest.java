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
package org.traffichunter.titan.core.channel;

import org.junit.jupiter.api.Test;
import org.traffichunter.titan.core.util.buffer.Buffer;
import org.traffichunter.titan.core.util.concurrent.ChannelPromise;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.traffichunter.titan.core.channel.ChannelWriteException.Reason.NOT_SENT;
import static org.traffichunter.titan.core.channel.ChannelWriteException.Reason.UNKNOWN;

/**
 * @author yun
 */
class ChannelWriteBufferTest {

    @Test
    void writes_past_the_high_watermark_are_still_accepted() {
        ChannelWriteBuffer writeBuffer = new ChannelWriteBuffer(8, 4);
        Buffer first = Buffer.direct().alloc(new byte[9]);
        Buffer second = Buffer.direct().alloc(new byte[8]);
        ChannelPromise secondPromise = promise();
        try {
            writeBuffer.append(first, promise());
            assertThat(writeBuffer.isWritable()).isFalse();

            writeBuffer.append(second, secondPromise);

            assertThat(secondPromise.isDone()).isFalse();
            assertThat(writeBuffer.pendingBytes()).isEqualTo(17);
            assertThat(second.byteBuf().refCnt()).isOne();
        } finally {
            writeBuffer.close();
        }
        assertThat(reasonOf(secondPromise)).isEqualTo(NOT_SENT);
    }

    @Test
    void attach_existing_buffer_state_to_event_loop_group_metrics() {
        ChannelWriteBuffer writeBuffer = new ChannelWriteBuffer(8, 4);
        writeBuffer.append(Buffer.heap().alloc(new byte[9]), promise());

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

        writeBuffer.append(first, promise());
        writeBuffer.append(second, promise());

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
        writeBuffer.append(payload, promise());

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
        ChannelWriteBuffer writeBuffer = new ChannelWriteBuffer(8, 4, metrics);
        writeBuffer.append(Buffer.heap().alloc(new byte[9]), promise());

        writeBuffer.close();
        writeBuffer.close();

        assertThat(metrics.getActiveBuffers()).isZero();
        assertThat(metrics.getPendingBytes()).isZero();
        assertThat(metrics.getNonWritableBuffers()).isZero();
    }

    @Test
    void consume_releases_only_the_completed_buffer() {
        AggregateChannelWriteBufferMetrics metrics = new AggregateChannelWriteBufferMetrics();
        ChannelWriteBuffer writeBuffer = new ChannelWriteBuffer(8, 4, metrics);
        Buffer first = Buffer.direct().alloc(new byte[5]);
        Buffer second = Buffer.direct().alloc(new byte[4]);
        try {
            writeBuffer.append(first, promise());
            writeBuffer.append(second, promise());

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
        ChannelWriteBuffer writeBuffer = new ChannelWriteBuffer(8, 4, metrics);
        Buffer first = Buffer.direct().alloc(new byte[5]);
        Buffer second = Buffer.direct().alloc(new byte[4]);
        try {
            writeBuffer.append(first, promise());
            writeBuffer.append(second, promise());

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

    @Test
    void promise_completes_when_the_last_byte_of_its_buffer_is_written() {
        ChannelWriteBuffer writeBuffer = new ChannelWriteBuffer();
        Buffer payload = Buffer.direct().alloc(new byte[6]);
        ChannelPromise promise = promise();
        try {
            writeBuffer.append(payload, promise);

            writeBuffer.consume(4);
            assertThat(promise.isDone()).isFalse();

            writeBuffer.consume(2);
            assertThat(promise.isSuccess()).isTrue();
            assertThat(payload.byteBuf().refCnt()).isZero();
            assertThat(writeBuffer.isEmpty()).isTrue();
        } finally {
            writeBuffer.close();
        }
    }

    @Test
    void promise_on_the_last_buffer_waits_for_every_buffer_of_the_request() {
        ChannelWriteBuffer writeBuffer = new ChannelWriteBuffer();
        Buffer first = Buffer.direct().alloc(new byte[3]);
        Buffer second = Buffer.direct().alloc(new byte[3]);
        Buffer last = Buffer.direct().alloc(new byte[3]);
        ChannelPromise promise = promise();
        try {
            writeBuffer.append(List.of(first, second, last), promise);

            writeBuffer.consume(3);
            writeBuffer.consume(3);
            writeBuffer.consume(2);
            assertThat(promise.isDone()).isFalse();
            assertThat(writeBuffer.current()).isSameAs(last);

            writeBuffer.consume(1);
            assertThat(promise.isSuccess()).isTrue();
            assertThat(writeBuffer.isEmpty()).isTrue();
        } finally {
            writeBuffer.close();
        }
    }

    @Test
    void empty_buffer_completes_its_promise_without_queueing() {
        ChannelWriteBuffer writeBuffer = new ChannelWriteBuffer();
        Buffer empty = Buffer.direct().alloc(new byte[0]);
        ChannelPromise promise = promise();
        try {
            writeBuffer.append(empty, promise);

            assertThat(promise.isSuccess()).isTrue();
            assertThat(empty.byteBuf().refCnt()).isZero();
            assertThat(writeBuffer.isEmpty()).isTrue();
            assertThat(writeBuffer.pendingBytes()).isZero();
        } finally {
            writeBuffer.close();
        }
    }



    @Test
    void append_after_close_fails_the_promise_and_releases_the_buffer() {
        ChannelWriteBuffer writeBuffer = new ChannelWriteBuffer();
        writeBuffer.close();
        Buffer payload = Buffer.direct().alloc(new byte[4]);
        ChannelPromise promise = promise();

        writeBuffer.append(payload, promise);

        assertThat(reasonOf(promise)).isEqualTo(NOT_SENT);
        assertThat(payload.byteBuf().refCnt()).isZero();
    }

    @Test
    void close_separates_the_part_written_request_from_those_never_started() {
        ChannelWriteBuffer writeBuffer = new ChannelWriteBuffer();
        Buffer firstHead = Buffer.direct().alloc(new byte[4]);
        Buffer firstTail = Buffer.direct().alloc(new byte[4]);
        Buffer second = Buffer.direct().alloc(new byte[4]);
        Buffer third = Buffer.direct().alloc(new byte[4]);
        ChannelPromise first = promise();
        ChannelPromise secondPromise = promise();
        ChannelPromise thirdPromise = promise();
        writeBuffer.append(List.of(firstHead, firstTail), first);
        writeBuffer.append(second, secondPromise);
        writeBuffer.append(third, thirdPromise);
        writeBuffer.consume(2);

        writeBuffer.close();

        assertThat(reasonOf(first)).isEqualTo(UNKNOWN);
        assertThat(reasonOf(secondPromise)).isEqualTo(NOT_SENT);
        assertThat(reasonOf(thirdPromise)).isEqualTo(NOT_SENT);
        assertThat(firstHead.byteBuf().refCnt()).isZero();
        assertThat(firstTail.byteBuf().refCnt()).isZero();
        assertThat(second.byteBuf().refCnt()).isZero();
        assertThat(third.byteBuf().refCnt()).isZero();
        assertThat(writeBuffer.pendingBytes()).isZero();
        assertThat(writeBuffer.isEmpty()).isTrue();
    }

    @Test
    void close_reports_not_started_when_no_byte_of_the_head_was_written() {
        ChannelWriteBuffer writeBuffer = new ChannelWriteBuffer();
        ChannelPromise promise = promise();
        writeBuffer.append(Buffer.direct().alloc(new byte[4]), promise);

        writeBuffer.close();

        assertThat(reasonOf(promise)).isEqualTo(NOT_SENT);
    }

    @Test
    void completed_request_does_not_mark_the_next_one_as_started() {
        ChannelWriteBuffer writeBuffer = new ChannelWriteBuffer();
        ChannelPromise done = promise();
        ChannelPromise pending = promise();
        writeBuffer.append(Buffer.direct().alloc(new byte[4]), done);
        writeBuffer.append(Buffer.direct().alloc(new byte[4]), pending);
        writeBuffer.consume(4);
        assertThat(done.isSuccess()).isTrue();

        writeBuffer.close();

        assertThat(reasonOf(pending)).isEqualTo(NOT_SENT);
    }


    @Test
    void empty_buffers_inside_a_request_are_dropped_and_the_promise_rides_on_the_last_readable_one() {
        ChannelWriteBuffer writeBuffer = new ChannelWriteBuffer();
        Buffer payload = Buffer.direct().alloc(new byte[3]);
        Buffer trailingEmpty = Buffer.direct().alloc(new byte[0]);
        ChannelPromise promise = promise();
        try {
            writeBuffer.append(List.of(Buffer.direct().alloc(new byte[0]), payload, trailingEmpty), promise);

            assertThat(trailingEmpty.byteBuf().refCnt()).isZero();
            assertThat(writeBuffer.pendingBytes()).isEqualTo(3);
            writeBuffer.consume(3);

            assertThat(promise.isSuccess()).isTrue();
            assertThat(writeBuffer.isEmpty()).isTrue();
        } finally {
            writeBuffer.close();
        }
    }

    @Test
    void request_of_only_empty_buffers_completes_without_queueing() {
        ChannelWriteBuffer writeBuffer = new ChannelWriteBuffer();
        ChannelPromise promise = promise();
        try {
            writeBuffer.append(List.of(Buffer.direct().alloc(new byte[0]), Buffer.direct().alloc(new byte[0])), promise);

            assertThat(promise.isSuccess()).isTrue();
            assertThat(writeBuffer.isEmpty()).isTrue();
        } finally {
            writeBuffer.close();
        }
    }

    private static ChannelWriteException.Reason reasonOf(ChannelPromise promise) {
        assertThat(promise.error()).isInstanceOf(ChannelWriteException.class);
        return ((ChannelWriteException) promise.error()).reason();
    }

    private static ChannelPromise promise() {
        EventLoop eventLoop = mock(EventLoop.class);
        when(eventLoop.inEventLoop()).thenReturn(true);
        return ChannelPromise.newPromise(eventLoop, mock(Channel.class));
    }
}
