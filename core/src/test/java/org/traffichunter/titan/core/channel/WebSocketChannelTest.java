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
import org.traffichunter.titan.core.channel.IOEventLoop;
import org.traffichunter.titan.core.channel.NetChannel.Internal;
import org.mockito.ArgumentCaptor;
import org.traffichunter.titan.core.channel.websocket.WebSocketChannel;
import org.traffichunter.titan.core.codec.websocket.WebSocketFrame;
import org.traffichunter.titan.core.codec.websocket.WebSocketFrameHeader;
import org.traffichunter.titan.core.util.concurrent.ChannelPromise;
import org.traffichunter.titan.core.util.Protocol;
import org.traffichunter.titan.core.util.buffer.Buffer;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
        WebSocketChannel channel = new WebSocketChannel(delegate, Protocol.STOMP);
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
        WebSocketChannel channel = new WebSocketChannel(delegate, Protocol.STOMP);
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
        verify(internal).write(encoded.capture());
        verify(internal).flush();
        assertThat(encoded.getValue().getBytes()).containsExactly((byte) 0x81, 0x02, 'O', 'K');
        assertThat(write.isSuccess()).isTrue();
        assertThat(write.channel()).isSameAs(delegate);

        encoded.getValue().release();
        payload.release();
    }

    @Test
    void reject_frame_with_different_subprotocol() {
        NetChannel delegate = mock(NetChannel.class);
        WebSocketChannel channel = new WebSocketChannel(delegate, Protocol.STOMP);
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
}
