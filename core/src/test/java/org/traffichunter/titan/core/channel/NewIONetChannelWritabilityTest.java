package org.traffichunter.titan.core.channel;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import org.junit.jupiter.api.Test;
import org.traffichunter.titan.core.util.buffer.Buffer;
import org.traffichunter.titan.core.util.concurrent.ChannelPromise;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NewIONetChannelWritabilityTest {
    @Test
    void transitions_are_deferred_and_forwarded_while_selector_interest_stays_separate() throws Exception {
        SocketChannel socket = mock(SocketChannel.class);
        IOEventLoop loop = mock(IOEventLoop.class);
        IOSelector selector = mock(IOSelector.class);
        when(loop.inEventLoop()).thenReturn(true);
        when(loop.ioSelector()).thenReturn(selector);
        Queue<Runnable> events = new ArrayDeque<>();
        doAnswer(call -> { events.add(call.getArgument(0)); return null; })
                .when(loop).execute(any(Runnable.class));
        NewIONetChannel channel = new NewIONetChannel(socket, ignored -> {});
        channel.register(loop, ChannelPromise.newPromise(loop, channel));
        List<Boolean> transitions = new ArrayList<>();
        channel.chain().addLast(new ChannelInBoundHandler() {});
        channel.chain().addLast(new ChannelInBoundHandler() {
            @Override
            public void sparkChannelWritabilityChanged(
                    NetChannel ch, boolean writable, ChannelInBoundHandlerChain chain) {
                transitions.add(writable);
                chain.sparkChannelWritabilityChanged(ch, writable);
            }
        });
        try {
            channel.internal().write(Buffer.heap().alloc(new byte[65 * 1024]), ChannelPromise.newPromise(loop, channel));
            channel.internal().write(Buffer.heap().alloc(new byte[1]), ChannelPromise.newPromise(loop, channel));
            assertThat(channel.isWritable()).isFalse();
            assertThat(transitions).isEmpty();
            events.remove().run();
            assertThat(transitions).containsExactly(false);
            assertThat(events).isEmpty();
            verifyNoInteractions(selector);

            when(socket.write(any(ByteBuffer.class))).thenReturn(0);
            channel.internal().flush();
            verify(selector).registerWrite(channel);
            assertThat(events).isEmpty();

            when(socket.write(any(ByteBuffer.class))).thenAnswer(call -> {
                ByteBuffer buffer = call.getArgument(0);
                // Deliberately consume in chunks: only one recovery event per flush.
                return Math.min(8192, buffer.remaining());
            });
            channel.internal().flush();
            verify(selector).unregisterWrite(channel);
            assertThat(channel.isWritable()).isTrue();
            assertThat(transitions).containsExactly(false);
            assertThat(events).hasSize(1);
            events.remove().run();
            assertThat(transitions).containsExactly(false, true);

            channel.internal().onWritabilityChanged(false);
            channel.close();
            events.remove().run();
            assertThat(transitions).containsExactly(false, true);
        } finally {
            channel.close();
        }
    }
}
