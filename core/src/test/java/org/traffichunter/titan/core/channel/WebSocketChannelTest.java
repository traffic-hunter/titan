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
import org.traffichunter.titan.core.channel.IOEventLoop;
import org.traffichunter.titan.core.channel.NetChannel.Internal;
import org.mockito.ArgumentCaptor;
import org.traffichunter.titan.core.channel.websocket.WebSocketChannel;
import org.traffichunter.titan.core.codec.websocket.WebSocketSide;
import org.traffichunter.titan.core.codec.websocket.WebSocketFrame;
import org.traffichunter.titan.core.codec.websocket.WebSocketFrameHeader;
import org.traffichunter.titan.core.util.concurrent.ChannelPromise;
import org.traffichunter.titan.core.util.concurrent.ScheduledPromise;
import org.traffichunter.titan.core.util.Protocol;
import org.traffichunter.titan.core.util.buffer.Buffer;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * @author yun
 */
class WebSocketChannelTest {

    @Test
    void preserve_delegate_channel_in_write_promise() throws Exception {
        ChannelSecondaryIOEventLoop eventLoop = new ChannelSecondaryIOEventLoop("websocket-channel-promise-test");
        InMemoryNetChannel delegate = new InMemoryNetChannel();
        eventLoop.start();
        delegate.register(eventLoop);
        WebSocketChannel channel = new WebSocketChannel(delegate, WebSocketSide.SERVER, Protocol.STOMP);
        Buffer payload = Buffer.heap().alloc("data");

        try {
            ChannelPromise write = channel.writeAndFlush(payload);
            write.await(2, TimeUnit.SECONDS);

            assertThat(write.isSuccess()).isTrue();
            assertThat(write.channel()).isSameAs(delegate);

            Buffer written = delegate.pollWritten();
            assertThat(written).isNotNull();
            if (written != null) {
                written.release();
            }
        } finally {
            if (payload.byteBuf().refCnt() > 0) {
                payload.release();
            }
            channel.close();
            eventLoop.gracefullyShutdown(1, TimeUnit.SECONDS);
        }
    }

    @Test
    void write_frame_directly_to_underlying_channel() {
        NetChannel delegate = mock(NetChannel.class);
        Internal internal = mock(Internal.class);
        IOEventLoop eventLoop = mock(IOEventLoop.class);
        when(delegate.internal()).thenReturn(internal);
        when(delegate.eventLoop()).thenReturn(eventLoop);
        when(eventLoop.inEventLoop()).thenReturn(true);
        WebSocketChannel channel = new WebSocketChannel(delegate, WebSocketSide.SERVER, Protocol.STOMP);
        Buffer payload = Buffer.heap().alloc("OK");
        WebSocketFrame frame = new WebSocketFrame(
                WebSocketFrameHeader.builder()
                        .op(WebSocketFrameHeader.OpCode.TEXT, true)
                        .payloadLength(payload.length())
                        .build(),
                payload,
                Protocol.STOMP
        );

        ChannelPromise write = channel.writeAndFlush(frame);

        ArgumentCaptor<Buffer> encoded = ArgumentCaptor.forClass(Buffer.class);
        ArgumentCaptor<ChannelPromise> promise = ArgumentCaptor.forClass(ChannelPromise.class);
        verify(internal).write(encoded.capture(), promise.capture());
        verify(internal).flush();
        assertThat(encoded.getValue().getBytes()).containsExactly((byte) 0x81, 0x02, 'O', 'K');
        // The transport owns completion now, so the returned promise is the one handed down.
        assertThat(promise.getValue()).isSameAs(write);
        assertThat(write.isDone()).isFalse();
        assertThat(write.channel()).isSameAs(delegate);

        encoded.getValue().release();
        payload.release();
    }

    @Test
    void reject_frame_with_different_subprotocol() {
        NetChannel delegate = mock(NetChannel.class);
        WebSocketChannel channel = new WebSocketChannel(delegate, WebSocketSide.SERVER, Protocol.STOMP);
        Buffer payload = Buffer.heap().alloc("data");
        WebSocketFrame frame = new WebSocketFrame(
                WebSocketFrameHeader.builder()
                        .op(WebSocketFrameHeader.OpCode.BINARY, true)
                        .payloadLength(payload.length())
                        .build(),
                payload,
                Protocol.MQTT
        );

        assertThatThrownBy(() -> channel.writeAndFlush(frame))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("subprotocol");

        payload.release();
    }

    @Test
    void send_normal_closure_before_closing_server_channel() {
        NetChannel delegate = activeChannel(true);
        WebSocketChannel channel = new WebSocketChannel(delegate, WebSocketSide.SERVER, Protocol.STOMP);

        channel.close();

        ArgumentCaptor<Buffer> written = ArgumentCaptor.forClass(Buffer.class);
        verify(delegate.internal()).write(written.capture(), any(ChannelPromise.class));
        verify(delegate).close();
        assertThat(written.getValue().getBytes()).containsExactly((byte) 0x88, 0x02, 0x03, (byte) 0xE8);

        written.getValue().release();
    }

    @Test
    void send_masked_normal_closure_from_client_channel() {
        NetChannel delegate = activeChannel(true);
        WebSocketChannel channel = new WebSocketChannel(delegate, WebSocketSide.CLIENT, Protocol.STOMP);

        channel.close();

        ArgumentCaptor<Buffer> written = ArgumentCaptor.forClass(Buffer.class);
        verify(delegate.internal()).write(written.capture(), any(ChannelPromise.class));
        byte[] bytes = written.getValue().getBytes();
        assertThat(bytes).hasSize(8);
        assertThat(bytes[0]).isEqualTo((byte) 0x88);
        assertThat(bytes[1]).isEqualTo((byte) 0x82);
        assertThat((byte) (bytes[6] ^ bytes[2])).isEqualTo((byte) 0x03);
        assertThat((byte) (bytes[7] ^ bytes[3])).isEqualTo((byte) 0xE8);
        verify(delegate).close();

        written.getValue().release();
    }

    @Test
    void close_inactive_channel_without_close_frame() {
        NetChannel delegate = activeChannel(true);
        when(delegate.isActive()).thenReturn(false);
        WebSocketChannel channel = new WebSocketChannel(delegate, WebSocketSide.SERVER, Protocol.STOMP);

        channel.close();

        verify(delegate.internal(), never()).write(any(Buffer.class), any(ChannelPromise.class));
        verify(delegate).close();
    }

    @Test
    void ignore_close_while_close_frame_is_pending() {
        NetChannel delegate = activeChannel(false);
        WebSocketChannel channel = new WebSocketChannel(delegate, WebSocketSide.SERVER, Protocol.STOMP);

        channel.close();
        channel.close();

        ArgumentCaptor<Buffer> written = ArgumentCaptor.forClass(Buffer.class);
        verify(delegate.internal(), times(1)).write(written.capture(), any(ChannelPromise.class));
        verify(delegate, never()).close();

        written.getValue().release();
    }

    @Test
    void close_channel_when_close_frame_is_not_written_in_time() {
        NetChannel delegate = activeChannel(false);
        WebSocketChannel channel = new WebSocketChannel(delegate, WebSocketSide.SERVER, Protocol.STOMP);

        channel.close();

        ArgumentCaptor<Runnable> timeout = ArgumentCaptor.forClass(Runnable.class);
        verify(delegate.eventLoop()).schedule(timeout.capture(), eq(5L), eq(TimeUnit.SECONDS));
        verify(delegate, never()).close();
        timeout.getValue().run();
        verify(delegate).close();

        ArgumentCaptor<Buffer> written = ArgumentCaptor.forClass(Buffer.class);
        verify(delegate.internal()).write(written.capture(), any(ChannelPromise.class));
        written.getValue().release();
    }

    /**
     * Returns an active channel whose writes complete on the calling thread when {@code completeWrites} is set.
     */
    private static NetChannel activeChannel(boolean completeWrites) {
        NetChannel delegate = mock(NetChannel.class);
        Internal internal = mock(Internal.class);
        IOEventLoop eventLoop = mock(IOEventLoop.class);
        when(delegate.internal()).thenReturn(internal);
        when(delegate.eventLoop()).thenReturn(eventLoop);
        when(delegate.isActive()).thenReturn(true);
        when(eventLoop.inEventLoop()).thenReturn(true);
        doReturn(mock(ScheduledPromise.class)).when(eventLoop).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
        if (completeWrites) {
            doAnswer(invocation -> {
                invocation.<ChannelPromise>getArgument(1).success();
                return null;
            }).when(internal).write(any(Buffer.class), any(ChannelPromise.class));
        }
        return delegate;
    }
}
