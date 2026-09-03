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
