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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.traffichunter.titan.core.codec.stomp.StompCommand;
import org.traffichunter.titan.core.codec.stomp.StompFrame;
import org.traffichunter.titan.core.codec.stomp.StompHeaders;
import org.traffichunter.titan.core.codec.stomp.StompHeaders.Elements;
import org.traffichunter.titan.core.codec.stomp.StompServerSubscriptions;
import org.traffichunter.titan.core.transport.stomp.option.StompServerOption;
import org.traffichunter.titan.core.transport.stomp.option.StompSessionOption;

/**
 * @author yun
 */
class StompVersionNegotiationTest {

    // stomp.js sends "1.2,1.1,1.0" and the Spring client sends "1.1,1.2".
    @ParameterizedTest
    @ValueSource(strings = {"1.2", "1.2,1.1,1.0", "1.1,1.2", "1.1, 1.2"})
    void accept_connect_that_offers_1_2(String acceptVersion) {
        StompClientChannel client = client();

        StompFrame sent = handleConnect(acceptVersion, client);

        assertThat(sent.getCommand()).isEqualTo(StompCommand.CONNECTED);
        assertThat(sent.getHeader(Elements.VERSION)).isEqualTo("1.2");
        verify(client, never()).close();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"1.0", "1.1", "1.0,1.1"})
    void reject_connect_without_1_2_and_list_supported_version(@Nullable String acceptVersion) {
        StompClientChannel client = client();

        StompFrame sent = handleConnect(acceptVersion, client);

        assertThat(sent.getCommand()).isEqualTo(StompCommand.ERROR);
        assertThat(sent.getHeader(Elements.VERSION)).isEqualTo("1.2");
        verify(client).close();
    }

    private static StompFrame handleConnect(@Nullable String acceptVersion, StompClientChannel client) {
        StompServerChannel server = mock(StompServerChannel.class);
        when(server.option()).thenReturn(StompServerOption.builder().build());
        when(server.subscriptions()).thenReturn(new StompServerSubscriptions());

        StompFrame connect = StompFrame.create(StompHeaders.create(), StompCommand.CONNECT);
        if (acceptVersion != null) {
            connect.addHeader(Elements.ACCEPT_VERSION, acceptVersion);
        }
        new StompServerHandlerImpl(server).handle(connect, client);

        ArgumentCaptor<StompFrame> sent = ArgumentCaptor.forClass(StompFrame.class);
        verify(client).send(sent.capture());
        return sent.getValue();
    }

    private static StompClientChannel client() {
        StompClientChannel client = mock(StompClientChannel.class);
        when(client.session()).thenReturn("session-1");
        when(client.version()).thenReturn("1.2");
        when(client.option()).thenReturn(StompSessionOption.builder().heartbeatX(0L).heartbeatY(0L).build());
        return client;
    }
}
