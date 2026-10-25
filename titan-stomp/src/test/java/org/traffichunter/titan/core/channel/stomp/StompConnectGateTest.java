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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.traffichunter.titan.core.codec.stomp.StompCommand;
import org.traffichunter.titan.core.codec.stomp.StompFrame;
import org.traffichunter.titan.core.codec.stomp.StompHeaders;
import org.traffichunter.titan.core.codec.stomp.StompHeaders.Elements;
import org.traffichunter.titan.core.codec.stomp.StompServerSubscriptions;
import org.traffichunter.titan.core.transport.stomp.option.StompServerOption;

/**
 * @author yun
 */
class StompConnectGateTest {

    private final StompServerSubscriptions subscriptions = new StompServerSubscriptions();
    private StompClientChannel client;

    @BeforeEach
    void setUp() {
        client = mock(StompClientChannel.class);
        when(client.session()).thenReturn("session-1");
    }

    @Test
    void reject_send_before_connect() {
        StompFrame send = frame(StompCommand.SEND);
        send.addHeader(Elements.DESTINATION, "/topic/a");

        handler(false).handle(send, client);

        assertThat(sentFrame().getHeader(Elements.MESSAGE)).isEqualTo("Not connected");
        verify(client).close();
    }

    @Test
    void reject_subscribe_before_connect_on_secured_server() {
        StompFrame subscribe = frame(StompCommand.SUBSCRIBE);
        subscribe.addHeader(Elements.ID, "0");
        subscribe.addHeader(Elements.DESTINATION, "/topic/a");

        handler(true).handle(subscribe, client);

        assertThat(sentFrame().getHeader(Elements.MESSAGE)).isEqualTo("Not connected");
        assertThat(subscriptions.size()).isZero();
        verify(client).close();
    }

    @Test
    void ignore_heartbeat_before_connect() {
        handler(false).handle(StompFrame.PING, client);

        verify(client, never()).send(any(StompFrame.class));
        verify(client, never()).close();
    }

    @Test
    void reject_second_connect() {
        when(client.isConnected()).thenReturn(true);
        StompFrame connect = frame(StompCommand.CONNECT);
        connect.addHeader(Elements.ACCEPT_VERSION, "1.2");

        handler(false).handle(connect, client);

        assertThat(sentFrame().getHeader(Elements.MESSAGE)).isEqualTo("Already connected");
        verify(client).close();
    }

    private StompServerHandlerImpl handler(boolean secured) {
        StompServerChannel server = mock(StompServerChannel.class);
        when(server.option()).thenReturn(StompServerOption.builder().secured(secured).build());
        when(server.subscriptions()).thenReturn(subscriptions);
        return new StompServerHandlerImpl(server);
    }

    private StompFrame sentFrame() {
        ArgumentCaptor<StompFrame> sent = ArgumentCaptor.forClass(StompFrame.class);
        verify(client).send(sent.capture());
        assertThat(sent.getValue().getCommand()).isEqualTo(StompCommand.ERROR);
        return sent.getValue();
    }

    private static StompFrame frame(StompCommand command) {
        return StompFrame.create(StompHeaders.create(), command);
    }
}
