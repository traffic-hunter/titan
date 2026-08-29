package org.traffichunter.titan.core.test.implementation.codec;

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.Test;
import org.traffichunter.titan.core.channel.ChannelInBoundHandlerChain;
import org.traffichunter.titan.core.channel.InMemoryNetChannel;
import org.traffichunter.titan.core.channel.IOEventLoop;
import org.traffichunter.titan.core.channel.NetChannel;
import org.traffichunter.titan.core.codec.ChannelDecoder;
import org.traffichunter.titan.core.codec.ChannelDecoderException;
import org.traffichunter.titan.core.util.concurrent.ChannelPromise;
import org.traffichunter.titan.core.util.buffer.Buffer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.DisplayNameGenerator.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author yun
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class ChannelDecoderTest {

    @Test
    void when_keeping_buffer() {
        Buffer keeping = Buffer.heap().alloc("alloc");
        Buffer in = Buffer.heap().alloc("in");

        Buffer expandBuffer = ChannelDecoder.MERGE_BUFFER.merge(keeping, in);

        assertThat(expandBuffer.toString()).isEqualTo("allocin");
        assertThat(keeping.byteBuf().refCnt()).isEqualTo(0);
        assertThat(in.byteBuf().refCnt()).isEqualTo(0);
        assertThat(expandBuffer.byteBuf().refCnt()).isEqualTo(1);
        expandBuffer.release();
    }

    @Test
    void when_decode_returns_null_after_consuming_then_no_frames() {
        Buffer in = Buffer.heap().alloc("drop");
        CollectingChain chain = new CollectingChain();
        ChannelDecoder decoder = new ChannelDecoder() {
            @Override
            protected Buffer decode(@NonNull NetChannel channel, @NonNull Buffer buffer) {
                buffer.skipBytes(buffer.length());
                return null;
            }
        };

        decoder.sparkChannelRead(new InMemoryNetChannel(), in, chain);

        assertThat(chain.frames).isEmpty();
        assertThat(in.byteBuf().refCnt()).isEqualTo(0);
    }

    @Test
    void release_pending_buffer_when_channel_closes() {
        Buffer input = Buffer.heap().alloc("partial");
        ChannelDecoder decoder = new ChannelDecoder() {
            @Override
            protected Buffer decode(@NonNull NetChannel channel, @NonNull Buffer buffer) {
                return null;
            }
        };
        InMemoryNetChannel channel = new InMemoryNetChannel();
        channel.chain().add(decoder);

        decoder.sparkChannelRead(channel, input, new CollectingChain());
        assertThat(input.byteBuf().refCnt()).isOne();

        channel.close();

        assertThat(input.byteBuf().refCnt()).isZero();
    }

    @Test
    void does_not_re_release_pending_buffer_when_decode_closes_channel() {
        Buffer input = Buffer.heap().alloc("DISCONNECT");
        InMemoryNetChannel channel = new InMemoryNetChannel();
        CollectingChain chain = new CollectingChain();

        ChannelDecoder decoder = new ChannelDecoder() {
            @Override
            protected Buffer decode(@NonNull NetChannel ch, @NonNull Buffer buffer) {
                buffer.skipBytes(buffer.length());
                ch.close();
                return null;
            }
        };
        channel.chain().add(decoder);

        assertThatCode(() -> decoder.sparkChannelRead(channel, input, chain))
                .doesNotThrowAnyException();

        assertThat(input.byteBuf().refCnt()).isZero();
        assertThat(chain.frames).isEmpty();
    }

    @Test
    void forwards_final_frame_and_releases_once_when_decode_closes_channel() {
        Buffer input = Buffer.heap().alloc("DISCONNECT");
        Buffer decoded = Buffer.heap().alloc("frame");
        InMemoryNetChannel channel = new InMemoryNetChannel();
        CollectingChain chain = new CollectingChain();

        ChannelDecoder decoder = new ChannelDecoder() {
            @Override
            protected Buffer decode(@NonNull NetChannel ch, @NonNull Buffer buffer) {
                buffer.skipBytes(buffer.length());
                ch.close();
                return decoded;
            }
        };
        channel.chain().add(decoder);

        assertThatCode(() -> decoder.sparkChannelRead(channel, input, chain))
                .doesNotThrowAnyException();

        assertThat(input.byteBuf().refCnt()).isZero();
        assertThat(chain.frames).containsExactly(decoded);
        decoded.release();
    }

    @Test
    void throws_when_decode_produces_frame_without_consuming_bytes() {
        Buffer input = Buffer.heap().alloc("frame");
        Buffer decoded = Buffer.heap().alloc("frame");
        InMemoryNetChannel channel = new InMemoryNetChannel();
        CollectingChain chain = new CollectingChain();

        ChannelDecoder decoder = new ChannelDecoder() {
            @Override
            protected Buffer decode(@NonNull NetChannel ch, @NonNull Buffer buffer) {
                // Returns a frame but never advances the reader index: a contract violation
                // that would otherwise re-emit the same frame on every subsequent read.
                return decoded;
            }
        };
        channel.chain().add(decoder);

        assertThatThrownBy(() -> decoder.sparkChannelRead(channel, input, chain))
                .isInstanceOf(ChannelDecoderException.class)
                .hasMessageContaining("without consuming any bytes");

        input.release();
        decoded.release();
    }

    @Test
    void schedule_decoder_cleanup_on_channel_event_loop() {
        AtomicReference<Runnable> cleanup = new AtomicReference<>();
        IOEventLoop eventLoop = mock(IOEventLoop.class);
        when(eventLoop.inEventLoop()).thenReturn(false);
        doAnswer(invocation -> {
            cleanup.set(invocation.getArgument(0));
            return null;
        }).when(eventLoop).execute(any(Runnable.class));

        InMemoryNetChannel channel = new InMemoryNetChannel();
        channel.register(eventLoop, mock(ChannelPromise.class));

        ChannelDecoder decoder = new ChannelDecoder() {
            @Override
            protected Buffer decode(@NonNull NetChannel channel, @NonNull Buffer buffer) {
                return null;
            }
        };
        channel.chain().add(decoder);

        Buffer input = Buffer.heap().alloc("partial");
        decoder.sparkChannelRead(channel, input, new CollectingChain());

        channel.close();

        assertThat(input.byteBuf().refCnt()).isOne();
        assertThat(cleanup.get()).isNotNull();

        cleanup.get().run();

        assertThat(input.byteBuf().refCnt()).isZero();
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
