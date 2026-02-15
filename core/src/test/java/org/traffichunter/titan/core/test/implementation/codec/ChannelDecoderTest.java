package org.traffichunter.titan.core.test.implementation.codec;

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.Test;
import org.traffichunter.titan.core.channel.ChannelInBoundHandlerChain;
import org.traffichunter.titan.core.channel.InMemoryNetChannel;
import org.traffichunter.titan.core.channel.NetChannel;
import org.traffichunter.titan.core.codec.ChannelDecoder;
import org.traffichunter.titan.core.util.buffer.Buffer;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.DisplayNameGenerator.*;

/**
 * @author yun
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class ChannelDecoderTest {

    @Test
    void when_keeping_buffer() {
        Buffer keeping = Buffer.alloc("alloc");
        Buffer in = Buffer.alloc("in");

        Buffer expandBuffer = ChannelDecoder.EXPANDING_AFTER_COPY_BUFFER.keep(keeping, in);

        assertThat(expandBuffer.toString()).isEqualTo("allocin");
        assertThat(keeping.byteBuf().refCnt()).isEqualTo(0);
        assertThat(in.byteBuf().refCnt()).isEqualTo(0);
        assertThat(expandBuffer.byteBuf().refCnt()).isEqualTo(1);
        expandBuffer.release();
    }

    @Test
    void when_decode_returns_null_after_consuming_then_no_frames() {
        Buffer in = Buffer.alloc("drop");
        CollectingChain chain = new CollectingChain();
        ChannelDecoder decoder = new ChannelDecoder() {
            @Override
            protected Buffer decode(@NonNull Buffer buffer) {
                buffer.skipBytes(buffer.length());
                return null;
            }
        };

        decoder.sparkChannelRead(new InMemoryNetChannel(), in, chain);

        assertThat(chain.frames).isEmpty();
        assertThat(in.byteBuf().refCnt()).isEqualTo(0);
    }

    private static final class CollectingChain implements ChannelInBoundHandlerChain {
        private final List<Buffer> frames = new ArrayList<>();

        @Override
        public void sparkChannelConnecting(@NonNull NetChannel channel) {
        }

        @Override
        public void sparkChannelAfterConnected(@NonNull NetChannel channel) {
        }

        @Override
        public void sparkChannelRead(@NonNull NetChannel channel, @NonNull Buffer buffer) {
            frames.add(buffer);
        }

        @Override
        public void sparkExceptionCaught(@NonNull Throwable error) {
        }
    }
}
