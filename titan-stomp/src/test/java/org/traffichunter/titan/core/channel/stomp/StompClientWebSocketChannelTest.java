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
import org.traffichunter.titan.core.channel.IOEventLoop;
import org.traffichunter.titan.core.channel.NetChannel;
import org.traffichunter.titan.core.channel.websocket.WebSocketChannel;
import org.traffichunter.titan.core.transport.stomp.option.StompSessionOption;
import org.traffichunter.titan.core.util.Protocol;

import static org.assertj.core.api.Assertions.assertThat;
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
        WebSocketChannel webSocketChannel = new WebSocketChannel(delegate, Protocol.STOMP);

        StompClientChannel channel = StompClientChannel.wrap(
                webSocketChannel,
                StompSessionOption.builder().build()
        );

        assertThat(channel).isInstanceOf(StompClientWebSocketChannel.class);
        assertThat(channel.channel()).isSameAs(webSocketChannel);
        assertThat(channel.version()).isEqualTo("1.2");
    }
}
