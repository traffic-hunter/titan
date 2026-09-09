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
import org.traffichunter.titan.core.codec.stomp.StompCommand;
import org.traffichunter.titan.core.codec.stomp.StompFrame;
import org.traffichunter.titan.core.codec.stomp.StompHeaders;
import org.traffichunter.titan.core.transport.stomp.option.StompSessionOption;
import org.traffichunter.titan.core.util.concurrent.Promise;

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
