package org.traffichunter.titan.core.channel;

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.Test;
import org.traffichunter.titan.core.util.buffer.Buffer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;

/**
 * Verifies that ChannelInBoundHandlerChainImpl releases the buffer when no next handler exists.
 *
 * @author yun gkdbssla97
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class ChannelInBoundHandlerChainImplTest {

    @Test
    void when_terminal_chain_then_buffer_refCnt_is_zero() {
        Buffer buf = Buffer.alloc("data");
        ChannelHandlerChain chain = new ChannelHandlerChain();

        chain.processChannelRead(new InMemoryNetChannel(), buf);

        assertThat(buf.byteBuf().refCnt())
                .as("terminal chain must release buffer")
                .isEqualTo(0);
    }

    @Test
    void when_pass_through_handler_then_buffer_released_at_chain_end() {
        Buffer buf = Buffer.alloc("data");
        ChannelHandlerChain chain = new ChannelHandlerChain();
        chain.add(new PassThroughHandler());

        chain.processChannelRead(new InMemoryNetChannel(), buf);

        assertThat(buf.byteBuf().refCnt())
                .as("buffer must be released after passing through all handlers")
                .isEqualTo(0);
    }

    @Test
    void when_handler_retains_buffer_then_refCnt_reflects_retain() {
        Buffer buf = Buffer.alloc("data");
        ChannelHandlerChain chain = new ChannelHandlerChain();
        chain.add(new RetainingHandler());

        chain.processChannelRead(new InMemoryNetChannel(), buf);

        // RetainingHandler called retain() → refCnt should be 1 after chain releases its ref
        assertThat(buf.byteBuf().refCnt())
                .as("explicit retain keeps buffer alive beyond chain release")
                .isEqualTo(1);

        buf.release();
    }

    private static class PassThroughHandler implements ChannelInBoundHandler {
        @Override
        public void sparkChannelRead(@NonNull NetChannel channel,
                                     @NonNull Buffer buffer,
                                     @NonNull ChannelInBoundHandlerChain chain) {
            chain.sparkChannelRead(channel, buffer);
        }
    }

    private static class RetainingHandler implements ChannelInBoundHandler {
        @Override
        public void sparkChannelRead(@NonNull NetChannel channel,
                                     @NonNull Buffer buffer,
                                     @NonNull ChannelInBoundHandlerChain chain) {
            buffer.retain();
            chain.sparkChannelRead(channel, buffer);
        }
    }
}
