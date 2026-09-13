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
import org.traffichunter.titan.core.channel.Channel;
import org.traffichunter.titan.core.channel.IOEventLoop;
import org.traffichunter.titan.core.channel.NetChannel;
import org.traffichunter.titan.core.codec.stomp.StompCommand;
import org.traffichunter.titan.core.codec.stomp.StompFrame;
import org.traffichunter.titan.core.codec.stomp.StompHeaders;
import org.traffichunter.titan.core.transport.stomp.option.StompSessionOption;
import org.traffichunter.titan.core.util.Handler;
import org.traffichunter.titan.core.util.concurrent.Promise;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author yun
 */
class StompClientTcpChannelTest {

    private static final Map<NetChannel, Object> closeHandlers = new IdentityHashMap<>();

    @Test
    void a_transport_that_closes_under_a_live_session_reports_a_dropped_connection() {
        NetChannel netChannel = netChannelWithChain();
        StompClientTcpChannel channel = connectedChannel(netChannel);
        AtomicReference<StompClientChannel> dropped = new AtomicReference<>();
        channel.connectionDroppedHandler(dropped::set);

        closeTransport(netChannel);

        // Nothing else notices a peer that walks away from a connection this side only publishes on.
        assertThat(dropped.get()).isSameAs(channel);
        assertThat(channel.isConnected()).isFalse();
    }

    @Test
    void a_session_closed_from_this_side_is_not_reported_as_dropped() {
        NetChannel netChannel = netChannelWithChain();
        StompClientTcpChannel channel = connectedChannel(netChannel);
        AtomicReference<StompClientChannel> dropped = new AtomicReference<>();
        channel.connectionDroppedHandler(dropped::set);

        channel.close();
        // The handler chain closes after the channel does, which must not look like a drop.
        closeTransport(netChannel);

        assertThat(dropped.get()).isNull();
    }

    private static StompClientTcpChannel connectedChannel(NetChannel netChannel) {
        StompClientTcpChannel channel = new StompClientTcpChannel(netChannel, StompSessionOption.DEFAULT);
        channel.connected();
        return channel;
    }

    /** A channel that remembers its close handler, so the test can close it the way the transport does. */
    private static NetChannel netChannelWithChain() {
        IOEventLoop eventLoop = mock(IOEventLoop.class);
        NetChannel netChannel = mock(NetChannel.class);
        when(netChannel.eventLoop()).thenReturn(eventLoop);
        when(netChannel.isConnected()).thenReturn(true);
        when(netChannel.closeHandler(any())).thenAnswer(invocation -> {
            closeHandlers.put(netChannel, invocation.getArgument(0));
            return netChannel;
        });
        return netChannel;
    }

    @SuppressWarnings("unchecked")
    private static void closeTransport(NetChannel netChannel) {
        ((Handler<Channel>) closeHandlers.get(netChannel)).handle(netChannel);
    }

    @Test
    void return_failed_promise_when_event_loop_rejects_send() {
        IOEventLoop eventLoop = mock(IOEventLoop.class);
        NetChannel netChannel = mock(NetChannel.class);
        when(netChannel.eventLoop()).thenReturn(eventLoop);
        when(netChannel.isActive()).thenReturn(true);
        when(netChannel.isConnected()).thenReturn(true);
        when(eventLoop.inEventLoop()).thenReturn(false);
        doThrow(new RejectedExecutionException("event loop stopped"))
                .when(eventLoop).execute(any(Runnable.class));

        StompClientTcpChannel channel = new StompClientTcpChannel(netChannel, StompSessionOption.DEFAULT);
        StompFrame frame = StompFrame.create(StompHeaders.create(), StompCommand.SEND);
        Promise<StompFrame> result = assertDoesNotThrow(() -> channel.send(frame));
        AtomicReference<Throwable> notified = new AtomicReference<>();
        assertDoesNotThrow(() -> result.onFailure(notified::set));

        assertThat(result.isFailed()).isTrue();
        assertThat(result.error())
                .isInstanceOf(StompNetChannelException.class)
                .hasCauseInstanceOf(RejectedExecutionException.class);
        assertThat(notified.get()).isNull();
    }
}
