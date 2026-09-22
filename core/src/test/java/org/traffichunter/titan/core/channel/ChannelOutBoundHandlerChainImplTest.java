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

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.Test;
import org.traffichunter.titan.core.util.buffer.Buffer;
import org.traffichunter.titan.core.util.concurrent.ChannelPromise;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;

/**
 * @author yun
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class ChannelOutBoundHandlerChainImplTest {

    @Test
    void terminal_chain_writes_to_internal_channel() {
        Buffer buffer = Buffer.heap().alloc("data");
        InMemoryNetChannel channel = new InMemoryNetChannel();
        ChannelOutBoundHandlerChainImpl chain = new ChannelOutBoundHandlerChainImpl();

        ChannelPromise promise = promise(channel);
        chain.sparkChannelWrite(channel, buffer, promise);
        assertThat(promise.isDone()).isFalse();
        channel.internal().flush();

        Buffer written = channel.pollWritten();
        assertThat(written).isNotNull();
        assertThat(written.getBytes()).containsExactly("data".getBytes());
        assertThat(promise.isSuccess()).isTrue();

        written.release();
        buffer.release();
    }

    @Test
    void addFirst_places_handler_before_existing_handlers() {
        Buffer buffer = Buffer.heap().alloc("data");
        List<String> order = new ArrayList<>();
        InMemoryNetChannel channel = new InMemoryNetChannel();
        ChannelOutBoundHandlerChainImpl chain = new ChannelOutBoundHandlerChainImpl()
                .addLast(new RecordingHandler("second", order))
                .addFirst(new RecordingHandler("first", order));

        chain.sparkChannelWrite(channel, buffer, promise(channel));
        channel.internal().flush();

        assertThat(order).containsExactly("first", "second");
        releaseWritten(channel);
        buffer.release();
    }

    @Test
    void remove_detaches_handler_and_preserves_tail() {
        Buffer buffer = Buffer.heap().alloc("data");
        List<String> order = new ArrayList<>();
        InMemoryNetChannel channel = new InMemoryNetChannel();
        RecordingHandler first = new RecordingHandler("first", order);
        RecordingHandler removed = new RecordingHandler("removed", order);
        RecordingHandler last = new RecordingHandler("last", order);
        ChannelOutBoundHandlerChainImpl chain = new ChannelOutBoundHandlerChainImpl()
                .addLast(first)
                .addLast(removed);

        assertThat(chain.remove(removed)).isTrue();
        chain.addLast(last);
        chain.sparkChannelWrite(channel, buffer, promise(channel));
        channel.internal().flush();

        assertThat(order).containsExactly("first", "last");
        releaseWritten(channel);
        buffer.release();
    }

    @Test
    void remove_returns_false_for_unknown_handler() {
        ChannelOutBoundHandlerChainImpl chain = new ChannelOutBoundHandlerChainImpl();

        assertThat(chain.remove(new RecordingHandler("unknown", new ArrayList<>()))).isFalse();
    }

    private static ChannelPromise promise(NetChannel channel) {
        IOEventLoop eventLoop = mock(IOEventLoop.class);
        when(eventLoop.inEventLoop()).thenReturn(true);
        return ChannelPromise.newPromise(eventLoop, channel);
    }

    private static void releaseWritten(InMemoryNetChannel channel) {
        Buffer written = channel.pollWritten();
        assertThat(written).isNotNull();
        written.release();
    }

    private record RecordingHandler(
            String name,
            List<String> order
    ) implements ChannelOutBoundHandler {

        @Override
        public void sparkChannelWrite(
                NetChannel channel,
                Buffer buffer,
                ChannelPromise promise,
                ChannelOutBoundHandlerChain chain
        ) {
            order.add(name);
            chain.sparkChannelWrite(channel, buffer, promise);
        }
    }
}
