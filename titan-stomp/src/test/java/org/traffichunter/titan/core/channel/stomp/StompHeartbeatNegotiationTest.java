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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.traffichunter.titan.core.codec.stomp.StompCommand;
import org.traffichunter.titan.core.codec.stomp.StompFrame;
import org.traffichunter.titan.core.codec.stomp.StompHeaders;
import org.traffichunter.titan.core.codec.stomp.StompHeaders.Elements;
import org.traffichunter.titan.core.codec.stomp.StompServerSubscriptions;
import org.traffichunter.titan.core.transport.stomp.option.StompServerOption;
import org.traffichunter.titan.core.transport.stomp.option.StompSessionOption;

/**
 * Both sides negotiate heartbeats against what they themselves offered.
 *
 * <p>A side that negotiates against a hardcoded default ends up policing an interval it never
 * agreed to. On the client that is fatal for a connection it only publishes on: nothing arrives to
 * refresh the idle clock, and the watchdog closes a healthy connection.</p>
 *
 * @author yun
 */
class StompHeartbeatNegotiationTest {

    @Test
    void a_client_that_asked_for_no_heartbeats_starts_no_timers() {
        StompClientChannel connection = connection(0, 0);

        new StompClientHandlerImpl().handle(connected("1000,1000"), connection);

        verify(connection).setHeartbeat(eq(0L), eq(0L), any());
    }

    @Test
    void a_client_that_asked_for_heartbeats_takes_the_slower_of_the_two_intervals() {
        StompClientChannel connection = connection(500, 2_000);

        new StompClientHandlerImpl().handle(connected("1000,1000"), connection);

        verify(connection).setHeartbeat(eq(1_000L), eq(2_000L), any());
    }

    @Test
    void a_client_that_will_not_send_heartbeats_still_accepts_the_ones_it_asked_for() {
        StompClientChannel connection = connection(0, 1_000);

        new StompClientHandlerImpl().handle(connected("1000,1000"), connection);

        verify(connection).setHeartbeat(eq(0L), eq(1_000L), any());
    }

    @Test
    void a_server_with_heartbeats_switched_off_asks_for_none_and_advertises_none() {
        StompServerChannel server = mock(StompServerChannel.class);
        when(server.option()).thenReturn(StompServerOption.builder().heartbeatX(0L).heartbeatY(0L).build());
        when(server.subscriptions()).thenReturn(new StompServerSubscriptions());
        StompClientChannel client = mock(StompClientChannel.class);
        when(client.session()).thenReturn("session-1");
        when(client.version()).thenReturn("1.2");
        when(client.option()).thenReturn(sessionOption(0, 0));

        new StompServerHandlerImpl(server).handle(connect("1000,1000"), client);

        verify(client).setHeartbeat(eq(0L), eq(0L), any());
        ArgumentCaptor<StompFrame> sent = ArgumentCaptor.forClass(StompFrame.class);
        verify(client).send(sent.capture());
        // The client negotiates against what this frame advertises, so it has to be the truth.
        assertThat(sent.getValue().getCommand()).isEqualTo(StompCommand.CONNECTED);
        assertThat(sent.getValue().getHeader(Elements.HEART_BEAT)).isEqualTo("0,0");
    }

    private static StompClientChannel connection(long heartbeatX, long heartbeatY) {
        StompClientChannel connection = mock(StompClientChannel.class);
        when(connection.option()).thenReturn(sessionOption(heartbeatX, heartbeatY));
        return connection;
    }

    private static StompSessionOption sessionOption(long heartbeatX, long heartbeatY) {
        return StompSessionOption.builder().heartbeatX(heartbeatX).heartbeatY(heartbeatY).build();
    }

    private static StompFrame connected(String heartbeat) {
        StompFrame frame = StompFrame.create(StompHeaders.create(), StompCommand.CONNECTED);
        frame.addHeader(Elements.HEART_BEAT, heartbeat);
        return frame;
    }

    private static StompFrame connect(String heartbeat) {
        StompFrame frame = StompFrame.create(StompHeaders.create(), StompCommand.CONNECT);
        frame.addHeader(Elements.ACCEPT_VERSION, "1.2");
        frame.addHeader(Elements.HEART_BEAT, heartbeat);
        return frame;
    }
}
