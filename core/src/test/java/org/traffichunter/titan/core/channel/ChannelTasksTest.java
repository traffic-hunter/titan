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
