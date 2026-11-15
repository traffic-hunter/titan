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
package org.traffichunter.titan.core.channel.stomp;

import org.junit.jupiter.api.Test;
import org.traffichunter.titan.core.util.concurrent.ChannelPromise;
import org.traffichunter.titan.core.channel.IOEventLoop;
import org.traffichunter.titan.core.channel.NetChannel;
import org.traffichunter.titan.core.channel.websocket.WebSocketChannel;
import org.traffichunter.titan.core.codec.stomp.StompCommand;
import org.traffichunter.titan.core.codec.stomp.StompFrame;
import org.traffichunter.titan.core.codec.stomp.StompHeaders;
import org.traffichunter.titan.core.codec.websocket.WebSocketSide;
import org.traffichunter.titan.core.transport.stomp.option.StompSessionOption;
import org.traffichunter.titan.core.util.Protocol;
import org.traffichunter.titan.core.util.buffer.Buffer;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author yun
 */
class StompClientWebSocketChannelTest {

    @Test
    void wrap_websocket_channel_as_stomp_client_channel() {
        NetChannel delegate = mock(NetChannel.class);
        when(delegate.eventLoop()).thenReturn(mock(IOEventLoop.class));
        WebSocketChannel webSocketChannel = new WebSocketChannel(delegate, WebSocketSide.SERVER, Protocol.STOMP);

        StompClientChannel channel = StompClientChannel.wrap(
                webSocketChannel,
                StompSessionOption.builder().build()
        );

        assertThat(channel).isInstanceOf(StompClientWebSocketChannel.class);
        assertThat(channel.channel()).isSameAs(webSocketChannel);
        assertThat(channel.version()).isEqualTo("1.2");
    }

    @Test
    void a_list_of_frames_is_written_as_one_websocket_message_each() {
        IOEventLoop eventLoop = mock(IOEventLoop.class);
        NetChannel delegate = mock(NetChannel.class);
        when(delegate.eventLoop()).thenReturn(eventLoop);
        when(delegate.isActive()).thenReturn(true);
        when(delegate.isConnected()).thenReturn(true);
        when(eventLoop.inEventLoop()).thenReturn(true);
        List<String> written = new ArrayList<>();
        when(delegate.writeAndFlush(any(Buffer.class))).thenAnswer(invocation -> {
            Buffer buffer = invocation.getArgument(0);
            written.add(buffer.toString());
            buffer.release();
            return ChannelPromise.newPromise(eventLoop, delegate);
        });
        StompClientChannel channel = StompClientChannel.wrap(
                new WebSocketChannel(delegate, WebSocketSide.SERVER, Protocol.STOMP),
                StompSessionOption.builder().build()
        );

        channel.send(List.of(
                StompFrame.create(StompHeaders.create(), StompCommand.MESSAGE, "one".getBytes(StandardCharsets.UTF_8)),
                StompFrame.create(StompHeaders.create(), StompCommand.MESSAGE, "two".getBytes(StandardCharsets.UTF_8))
        ));

        // The transport turns each write into one WebSocket message, which holds one STOMP frame.
        assertThat(written).containsExactly(
                "MESSAGE\r\ncontent-length:3\r\n\r\none\u0000",
                "MESSAGE\r\ncontent-length:3\r\n\r\ntwo\u0000"
        );
    }
}
