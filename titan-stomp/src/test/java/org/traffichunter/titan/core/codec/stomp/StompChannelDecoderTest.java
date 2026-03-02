package org.traffichunter.titan.core.codec.stomp;

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.Test;
import org.traffichunter.titan.core.channel.ChannelInBoundHandlerChain;
import org.traffichunter.titan.core.channel.InMemoryNetChannel;
import org.traffichunter.titan.core.channel.NetChannel;
import org.traffichunter.titan.core.channel.stomp.StompHandler;
import org.traffichunter.titan.core.channel.stomp.StompNetChannel;
import org.traffichunter.titan.core.util.IdGenerator;
import org.traffichunter.titan.core.util.buffer.Buffer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.DisplayNameGenerator.*;

/**
 * @author yun
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class StompChannelDecoderTest {

    @Test
    void decode_stomp_test() {
        StompCommand command = StompCommand.CONNECT;
        StompHeaders headers = StompHeaders.create();
        headers.put(StompHeaders.Elements.ID, IdGenerator.uuid());

        StompFrame stompFrame = StompFrame.create(headers, command, Buffer.alloc("hello"));
        Buffer frames = stompFrame.toBuffer();

        try {
            TestStompChannelDecoder decoder = new TestStompChannelDecoder(64, ((sf, sc) -> {}));
            Buffer result = decoder.decode(new InMemoryNetChannel(), frames);

            assertThat(result).isEqualTo(stompFrame.toBuffer());
        } finally {
            frames.release();
        }
    }

    @Test
    void when_not_end_of_line_then_return_null() {
        StompCommand command = StompCommand.CONNECT;
        StompHeaders headers = StompHeaders.create();
        headers.put(StompHeaders.Elements.ID, IdGenerator.uuid());

        StompFrame stompFrame = StompFrame.create(headers, command, Buffer.alloc("hello"));
        Buffer frame = stompFrame.toBuffer();

        Buffer notEofFrame = frame.readSlice(frame.length() - 1);

        try {
            TestStompChannelDecoder decoder = new TestStompChannelDecoder(64, ((sf, sc) -> {}));

            Buffer decode = decoder.decode(new InMemoryNetChannel(), notEofFrame);

            assertThat(decode).isNull();
        } finally {
            notEofFrame.release();
        }
    }

    @Test
    void when_content_length_mismatch_then_err() {
        StompHeaders headers = StompHeaders.create();
        headers.put(StompHeaders.Elements.CONTENT_LENGTH, "10");

        StompFrame frame = StompFrame.create(headers, StompCommand.SEND, Buffer.alloc("hello"));
        Buffer buf = frame.toBuffer();

        try {
            TestStompChannelDecoder decoder = new TestStompChannelDecoder(64, ((sf, sc) -> {}));
            Buffer result = decoder.decode(new InMemoryNetChannel(), buf);

            assertThat(result).isEqualTo(StompFrame.ERR_STOMP_FRAME.toBuffer());
        } finally {
            buf.release();
        }
    }

    @Test
    void decode_with_content_length_ok() {
        StompHeaders headers = StompHeaders.create();
        headers.put(StompHeaders.Elements.CONTENT_LENGTH, "5");

        StompFrame frame = StompFrame.create(headers, StompCommand.SEND, Buffer.alloc("hello"));

        Buffer buf = frame.toBuffer();

        try {
            TestStompChannelDecoder decoder = new TestStompChannelDecoder(64, ((sf, sc) -> {}));
            Buffer result = decoder.decode(new InMemoryNetChannel(), buf);

            assertThat(result).isEqualTo(frame.toBuffer());
        } finally {
            buf.release();
        }
    }

    @Test
    void decode_no_body_frame() {
        StompHeaders headers = StompHeaders.create();
        headers.put(StompHeaders.Elements.ID, "1");

        StompFrame frame = StompFrame.create(headers, StompCommand.CONNECT, Buffer.empty());
        Buffer buf = frame.toBuffer();

        try {
            TestStompChannelDecoder decoder = new TestStompChannelDecoder(64, ((sf, sc) -> {}));
            Buffer result = decoder.decode(new InMemoryNetChannel(), buf);

            assertThat(result).isEqualTo(frame.toBuffer());
        } finally {
            buf.release();
        }
    }

    @Test
    void shouldDecodeOnlyFirstStompFrame_whenMultipleFramesExistInBuffer() {
        StompHeaders headers = StompHeaders.create();
        headers.put(StompHeaders.Elements.ID, "1");

        StompFrame stompFrame = StompFrame.create(headers, StompCommand.SEND, Buffer.alloc("hello"));

        Buffer stompFrames = Buffer.alloc(stompFrame + "CONNECT\r\nid:1\r");

        try {
            TestStompChannelDecoder decoder = new TestStompChannelDecoder(64, ((sf, sc) -> {}));
            Buffer result = decoder.decode(new InMemoryNetChannel(), stompFrames);

            assertThat(result).isEqualTo(stompFrame.toBuffer());
        }  finally {
            stompFrames.release();
        }
    }

    @Test
    void when_frame_is_split_then_emit_after_second_chunk() {
        StompHeaders headers = StompHeaders.create();
        headers.put(StompHeaders.Elements.ID, "1");

        StompFrame stompFrame = StompFrame.create(headers, StompCommand.SEND, Buffer.alloc("hello"));
        Buffer total = stompFrame.toBuffer();
        byte[] bytes = total.getBytes();

        int split = bytes.length / 2;
        Buffer part1 = Buffer.alloc(Arrays.copyOfRange(bytes, 0, split));
        Buffer part2 = Buffer.alloc(Arrays.copyOfRange(bytes, split, bytes.length));

        CollectingChain chain = new CollectingChain();
        TestStompChannelDecoder decoder = new TestStompChannelDecoder(64, ((sf, sc) -> {}));

        NetChannel channel = new InMemoryNetChannel();

        try {
            decoder.sparkChannelRead(channel, part1, chain);
            assertThat(chain.frames).isEmpty();

            decoder.sparkChannelRead(channel, part2, chain);
            assertThat(chain.frames).hasSize(1);
            assertThat(chain.frames.getFirst().getBytes()).isEqualTo(bytes);
        } finally {
            total.release();
            chain.releaseAll();
        }
    }

    private static class TestStompChannelDecoder extends StompChannelDecoder {

        public TestStompChannelDecoder(int maxLength, StompHandler handler) {
            super(maxLength, StompNetChannel.open(new InMemoryNetChannel(), StompVersion.STOMP_1_2), handler);
        }
    }

    private static class CollectingChain implements ChannelInBoundHandlerChain {
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

        private void releaseAll() {
            for (Buffer frame : frames) {
                frame.release();
            }
            frames.clear();
        }
    }
}
