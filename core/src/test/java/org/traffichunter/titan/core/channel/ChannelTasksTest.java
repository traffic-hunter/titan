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
import org.traffichunter.titan.core.util.concurrent.Promise;

import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author yun
 */
class ChannelTasksTest {

    @Test
    void return_failed_promise_when_event_loop_rejects_channel_task() {
        IOEventLoop eventLoop = rejectingEventLoop();

        Promise<Void> result = ChannelTasks.execute(eventLoop, () -> { });

        assertRejected(result);
    }

    @Test
    void return_failed_promise_when_event_loop_rejects_accept() {
        IOEventLoop eventLoop = rejectingEventLoop();
        NetServerChannel channel = mock(NetServerChannel.class);
        when(channel.eventLoop()).thenReturn(eventLoop);

        Promise<NetChannel> result = ChannelTasks.accept(channel);

        assertRejected(result);
    }

    private static IOEventLoop rejectingEventLoop() {
        IOEventLoop eventLoop = mock(IOEventLoop.class);
        when(eventLoop.inEventLoop()).thenReturn(false);
        doThrow(new RejectedExecutionException("event loop stopped"))
                .when(eventLoop).execute(any(Runnable.class));
        return eventLoop;
    }

    private static void assertRejected(Promise<?> result) {
        assertThat(result.isFailed()).isTrue();
        assertThat(result.error())
                .isInstanceOf(RejectedExecutionException.class)
                .hasMessage("event loop stopped");
    }
}
