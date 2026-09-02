package org.traffichunter.titan.core.codec.stomp;

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.traffichunter.titan.core.channel.ChannelInBoundHandlerChain;
import org.traffichunter.titan.core.channel.InMemoryNetChannel;
import org.traffichunter.titan.core.channel.IOEventLoop;
import org.traffichunter.titan.core.channel.NetChannel;
import org.traffichunter.titan.core.channel.stomp.StompHandler;
import org.traffichunter.titan.core.channel.stomp.StompClientChannel;
import org.traffichunter.titan.core.util.concurrent.ChannelPromise;
import org.traffichunter.titan.core.util.concurrent.Promise;
import org.traffichunter.titan.core.transport.stomp.option.StompSessionOption;
import org.traffichunter.titan.core.util.IdGenerator;
import org.traffichunter.titan.core.util.buffer.Buffer;
import org.mockito.Mockito;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.DisplayNameGenerator.*;

/**
 * @author yun, gkdbssla97
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class StompChannelDecoderTest {

    @Test
    void decode_stomp_test() {
        StompCommand command = StompCommand.CONNECT;
        StompHeaders headers = StompHeaders.create();
        headers.put(StompHeaders.Elements.ID, IdGenerator.uuid());

        StompFrame stompFrame = StompFrame.create(headers, command, Buffer.heap().alloc("hello"));
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

        StompFrame stompFrame = StompFrame.create(headers, command, Buffer.heap().alloc("hello"));
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

        StompFrame frame = StompFrame.create(headers, StompCommand.SEND, Buffer.heap().alloc("hello"));
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

        StompFrame frame = StompFrame.create(headers, StompCommand.SEND, Buffer.heap().alloc("hello"));

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

        StompFrame frame = StompFrame.create(headers, StompCommand.CONNECT, Buffer.heap().empty());
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

        StompFrame stompFrame = StompFrame.create(headers, StompCommand.SEND, Buffer.heap().alloc("hello"));

        Buffer stompFrames = Buffer.heap().alloc(stompFrame + "CONNECT\r\nid:1\r");

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

        StompFrame stompFrame = StompFrame.create(headers, StompCommand.SEND, Buffer.heap().alloc("hello"));
        Buffer total = stompFrame.toBuffer();
        byte[] bytes = total.getBytes();

        int split = bytes.length / 2;
        Buffer part1 = Buffer.heap().alloc(Arrays.copyOfRange(bytes, 0, split));
        Buffer part2 = Buffer.heap().alloc(Arrays.copyOfRange(bytes, split, bytes.length));

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

    @Test
    @Timeout(5)
    void reject_oversized_frame_without_nul_before_handling_frame() {
        List<StompFrame> handled = new ArrayList<>();
        CollectingChain chain = new CollectingChain();
        Buffer input = Buffer.direct().alloc("SEND\ndestination:/queue/a\n\n" + "A".repeat(65));

        // Issue #125: no CONNECT or NUL is needed to reach the accumulation path.
        try (TestStompChannelDecoder decoder = new TestStompChannelDecoder(64,
                (frame, connection) -> handled.add(frame))) {
            assertThatCode(() -> decoder.sparkChannelRead(new InMemoryNetChannel(), input, chain))
                    .doesNotThrowAnyException();
            assertThat(handled).isEmpty();
            assertThat(chain.frames).isEmpty();
        } finally {
            chain.releaseAll();
        }
        assertThat(input.byteBuf().refCnt()).isZero();
    }

    @Test
    @Timeout(5)
    void send_error_frame_and_close_connection_when_frame_limit_is_exceeded() {
        StompClientChannel stompChannel = Mockito.mock(StompClientChannel.class);
        @SuppressWarnings("unchecked")
        Promise<StompFrame> writeResult = Mockito.mock(Promise.class);
        Mockito.when(stompChannel.session()).thenReturn("oversized-frame-test");
        Mockito.when(stompChannel.send(Mockito.any(StompFrame.class))).thenReturn(writeResult);
        Mockito.when(writeResult.onSuccess(Mockito.any())).thenAnswer(invocation -> {
            Consumer<StompFrame> success = invocation.getArgument(0);
            success.accept(Mockito.mock(StompFrame.class));
            return writeResult;
        });
        Mockito.when(writeResult.onFailure(Mockito.any())).thenReturn(writeResult);

        Buffer input = Buffer.direct().alloc("SEND\ndestination:/queue/a\n\n" + "A".repeat(65));
        CollectingChain chain = new CollectingChain();
        try (StompChannelDecoder decoder = new StompChannelDecoder(64, stompChannel, (frame, connection) -> {})) {
            decoder.sparkChannelRead(new InMemoryNetChannel(), input, chain);

            ArgumentCaptor<StompFrame> errorFrame = ArgumentCaptor.forClass(StompFrame.class);
            Mockito.verify(stompChannel).send(errorFrame.capture());
            assertThat(errorFrame.getValue().command()).isEqualTo(StompCommand.ERROR);
            assertThat(errorFrame.getValue().getHeader(StompHeaders.Elements.MESSAGE))
                    .isEqualTo("Frame size limit exceeded.");
            Mockito.verify(stompChannel).close();
            assertThat(chain.frames).isEmpty();
        } finally {
            chain.releaseAll();
        }
        assertThat(input.byteBuf().refCnt()).isZero();
    }

    @Test
    @Timeout(5)
    void reject_oversized_frame_accumulated_across_reads_without_nul() {
        List<StompFrame> handled = new ArrayList<>();
        CollectingChain chain = new CollectingChain();
        NetChannel channel = new InMemoryNetChannel();

        try (TestStompChannelDecoder decoder = new TestStompChannelDecoder(64,
                (frame, connection) -> handled.add(frame))) {
            decoder.sparkChannelRead(channel,
                    Buffer.direct().alloc("SEND\ndestination:/queue/a\n\n"), chain);

            // Bound the reproduction instead of exhausting the JVM heap.
            assertThatCode(() -> {
                for (int i = 0; i < 8; i++) {
                    decoder.sparkChannelRead(channel, Buffer.direct().alloc("A".repeat(16)), chain);
                }
            }).doesNotThrowAnyException();
            assertThat(handled).isEmpty();
            assertThat(chain.frames).isEmpty();
        } finally {
            chain.releaseAll();
        }
    }

    @Test
    @Timeout(5)
    void reject_oversized_completed_frame() {
        List<StompFrame> handled = new ArrayList<>();
        CollectingChain chain = new CollectingChain();
        Buffer input = Buffer.direct().alloc("SEND\n\n" + "A".repeat(59) + "\0");

        try (TestStompChannelDecoder decoder = new TestStompChannelDecoder(64,
                (frame, connection) -> handled.add(frame))) {
            assertThatCode(() -> decoder.sparkChannelRead(new InMemoryNetChannel(), input, chain))
                    .doesNotThrowAnyException();
            assertThat(handled).isEmpty();
            assertThat(chain.frames).isEmpty();
        } finally {
            chain.releaseAll();
        }
        assertThat(input.byteBuf().refCnt()).isZero();
    }

    @Test
    @Timeout(5)
    void enforce_frame_limit_by_encoded_byte_length() {
        List<StompFrame> handled = new ArrayList<>();
        CollectingChain chain = new CollectingChain();
        String payload = "한".repeat(20);
        Buffer input = Buffer.direct().alloc("SEND\n\n" + payload);

        assertThat(payload.length()).isLessThan(64);
        assertThat(input.length()).isGreaterThan(64);

        try (TestStompChannelDecoder decoder = new TestStompChannelDecoder(64,
                (frame, connection) -> handled.add(frame))) {
            assertThatCode(() -> decoder.sparkChannelRead(new InMemoryNetChannel(), input, chain))
                    .doesNotThrowAnyException();
            assertThat(handled).isEmpty();
            assertThat(chain.frames).isEmpty();
        } finally {
            chain.releaseAll();
        }
        assertThat(input.byteBuf().refCnt()).isZero();
    }

    @Test
    @Timeout(5)
    void decode_fragmented_frame_at_limit_only_after_nul_arrives() {
        List<StompFrame> handled = new ArrayList<>();
        CollectingChain chain = new CollectingChain();
        NetChannel channel = new InMemoryNetChannel();
        String head = "SEND\ndestination:/queue/a\ncontent-length:64\n\n";
        String body = "A".repeat(64);
        int maxFrameLength = head.length() + body.length();

        try (TestStompChannelDecoder decoder = new TestStompChannelDecoder(maxFrameLength,
                (frame, connection) -> handled.add(frame))) {
            decoder.sparkChannelRead(channel, Buffer.direct().alloc(head), chain);
            for (int i = 0; i < 4; i++) {
                decoder.sparkChannelRead(channel, Buffer.direct().alloc("A".repeat(16)), chain);
                assertThat(handled).isEmpty();
                assertThat(chain.frames).isEmpty();
            }

            decoder.sparkChannelRead(channel, Buffer.direct().alloc(new byte[]{0}), chain);

            assertThat(handled).singleElement().satisfies(frame -> {
                assertThat(frame.command()).isEqualTo(StompCommand.SEND);
                assertThat(frame.body()).isEqualTo(body.getBytes(StandardCharsets.US_ASCII));
            });
            assertThat(chain.frames).hasSize(1);
        } finally {
            chain.releaseAll();
        }
    }

    @Test
    @Timeout(5)
    void decode_multiple_valid_frames_when_combined_read_exceeds_limit() {
        List<StompFrame> handled = new ArrayList<>();
        CollectingChain chain = new CollectingChain();
        String frameText = "SEND\ndestination:/queue/a\ncontent-length:16\n\n" + "A".repeat(16) + "\0";
        Buffer input = Buffer.direct().alloc(frameText.repeat(8));

        try (TestStompChannelDecoder decoder = new TestStompChannelDecoder(64,
                (frame, connection) -> handled.add(frame))) {
            assertThat(input.length()).isGreaterThan(64);
            decoder.sparkChannelRead(new InMemoryNetChannel(), input, chain);

            assertThat(handled).hasSize(8).allSatisfy(frame -> {
                assertThat(frame.command()).isEqualTo(StompCommand.SEND);
                assertThat(frame.body()).isEqualTo("A".repeat(16).getBytes(StandardCharsets.US_ASCII));
            });
            assertThat(chain.frames).hasSize(8);
            assertThat(input.byteBuf().refCnt()).isZero();
        } finally {
            chain.releaseAll();
        }
    }

    @Test
    @Timeout(5)
    void release_incomplete_frame_when_decoder_closes() {
        CollectingChain chain = new CollectingChain();
        Buffer input = Buffer.direct().alloc("SEND\ndestination:/queue/a\n\npartial");

        try (TestStompChannelDecoder decoder = new TestStompChannelDecoder(64, (frame, connection) -> {})) {
            decoder.sparkChannelRead(new InMemoryNetChannel(), input, chain);
            assertThat(chain.frames).isEmpty();
            assertThat(input.byteBuf().refCnt()).isOne();
        } finally {
            chain.releaseAll();
        }

        assertThat(input.byteBuf().refCnt()).isZero();
    }

    // ── refCnt leak regression tests ──────────────────────────────────────────

    @Test
    void decode_result_refCnt_is_zero_when_no_downstream_handler() {
        // Leak 2 regression: ChannelInBoundHandlerChainImpl must release the buffer
        // returned by decode() when it reaches the end of the chain (next == null).
        StompHeaders headers = StompHeaders.create();
        headers.put(StompHeaders.Elements.ID, IdGenerator.uuid());
        StompFrame frame = StompFrame.create(headers, StompCommand.CONNECT, Buffer.heap().alloc("hello"));
        Buffer buf = frame.toBuffer();

        TerminalChain terminal = new TerminalChain();
        TestStompChannelDecoder decoder = new TestStompChannelDecoder(64, ((sf, sc) -> {}));

        try {
            decoder.sparkChannelRead(new InMemoryNetChannel(), buf, terminal);
            assertThat(terminal.received)
                    .as("terminal chain must have received the decoded buffer")
                    .isNotNull();
            assertThat(terminal.received.byteBuf().refCnt())
                    .as("buffer refCnt must be 0 after terminal chain releases it")
                    .isEqualTo(0);
        } finally {
            // buf itself is consumed by keepingBuffer inside ChannelDecoder
            // and released when keepingBuffer is drained — no manual release needed here
        }
    }

    @Test
    void when_content_length_mismatch_then_parse_buffers_released() {
        // Leak 3 regression: stompFrame and frames list inside StompParser.parse()
        // must be released even when ERR_STOMP_FRAME is returned early.
        StompHeaders headers = StompHeaders.create();
        headers.put(StompHeaders.Elements.CONTENT_LENGTH, "10");
        StompFrame frame = StompFrame.create(headers, StompCommand.SEND, Buffer.heap().alloc("hello"));
        Buffer buf = frame.toBuffer();

        TerminalChain terminal = new TerminalChain();
        TestStompChannelDecoder decoder = new TestStompChannelDecoder(64, ((sf, sc) -> {}));
        decoder.sparkChannelRead(new InMemoryNetChannel(), buf, terminal);

        // If stompFrame/frames buffers were not released, ResourceLeakDetector (PARANOID)
        // would report a leak. Here we verify the decode completes without error
        // and the output buffer is properly handled.
        assertThat(terminal.received).isNotNull();
        assertThat(terminal.received.byteBuf().refCnt()).isEqualTo(0);
    }

    private static class TerminalChain implements ChannelInBoundHandlerChain {
        Buffer received;

        @Override
        public void sparkChannelConnecting(@NonNull NetChannel channel) {}

        @Override
        public void sparkChannelAfterConnected(@NonNull NetChannel channel) {}

        @Override
        public void sparkChannelRead(@NonNull NetChannel channel, @NonNull Buffer buffer) {
            received = buffer;
            buffer.release();
        }

        @Override
        public void sparkExceptionCaught(@NonNull Throwable error) {}
    }

    private static class TestStompChannelDecoder extends StompChannelDecoder {

        public TestStompChannelDecoder(int maxLength, StompHandler handler) {
            super(maxLength, StompClientChannel.wrap(channelWithEventLoop(), StompSessionOption.builder().build()), handler);
        }

        private static InMemoryNetChannel channelWithEventLoop() {
            InMemoryNetChannel channel = new InMemoryNetChannel();
            IOEventLoop eventLoop = Mockito.mock(IOEventLoop.class);
            channel.register(eventLoop, ChannelPromise.newPromise(eventLoop, channel));
            return channel;
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
