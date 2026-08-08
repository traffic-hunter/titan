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

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.Test;
import org.traffichunter.titan.core.util.buffer.Buffer;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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

        chain.sparkChannelWrite(channel, buffer);
        channel.internal().flush();

        Buffer written = channel.pollWritten();
        assertThat(written).isNotNull();
        assertThat(written.getBytes()).containsExactly("data".getBytes());

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

        chain.sparkChannelWrite(channel, buffer);
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
        chain.sparkChannelWrite(channel, buffer);
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
                ChannelOutBoundHandlerChain chain
        ) {
            order.add(name);
            chain.sparkChannelWrite(channel, buffer);
        }
    }
}
