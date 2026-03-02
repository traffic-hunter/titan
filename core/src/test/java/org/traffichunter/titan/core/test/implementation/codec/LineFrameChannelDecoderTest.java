package org.traffichunter.titan.core.test.implementation.codec;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.traffichunter.titan.core.channel.InMemoryNetChannel;
import org.traffichunter.titan.core.channel.NetChannel;
import org.traffichunter.titan.core.codec.LineFrameChannelDecoder;
import org.traffichunter.titan.core.util.buffer.Buffer;

import java.util.LinkedList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * @author yun
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class LineFrameChannelDecoderTest {

    @Test
    void shouldReturnFrame_whenDelimiterIsCRLF() {
        TestLineFrameChannelDecoder decoder = new TestLineFrameChannelDecoder(10);

        Buffer buffer = Buffer.alloc("hello\r\nhello\r\n");
        List<Buffer> frames = decoder.decodes(buffer);

        try {
            assertThat(frames).hasSize(2);
            assertThat(frames).allMatch(buf -> buf.toString().equals("hello"));
        } finally {
            frames.forEach(Buffer::release);
            buffer.release();
        }
    }

    @Test
    void shouldReturnFrame_whenDelimiterIsLF() {
        TestLineFrameChannelDecoder decoder = new TestLineFrameChannelDecoder(10);

        Buffer buffer = Buffer.alloc("hello\nhello\n");
        List<Buffer> frames = decoder.decodes(buffer);

        try {
            assertThat(frames).hasSize(2);
            assertThat(frames).allMatch(buf -> buf.toString().equals("hello"));
        } finally {
            frames.forEach(Buffer::release);
            buffer.release();
        }
    }

    @Test
    void shouldReturnIsEmpty_whenDelimiterNotFound() {
        TestLineFrameChannelDecoder decoder = new TestLineFrameChannelDecoder(10);

        Buffer buffer = Buffer.alloc("hello");
        List<Buffer> frames = decoder.decodes(buffer);

        try {
            assertThat(frames).isEmpty();
        } finally {
            frames.forEach(Buffer::release);
            buffer.release();
        }
    }

    @Test
    void shouldReturnIsEmpty_whenFrameExceedsMaxLength_withDelimiter() {
        TestLineFrameChannelDecoder decoder = new TestLineFrameChannelDecoder(10);

        Buffer buffer = Buffer.alloc("hellohellohello\r\n");
        List<Buffer> frames = decoder.decodes(buffer);

        try {
            assertThat(frames).isEmpty();
        } finally {
            frames.forEach(Buffer::release);
            buffer.release();
        }
    }

    @Test
    void shouldReturnIsEmpty_whenFrameExceedsMaxLength_withoutDelimiter() {
        TestLineFrameChannelDecoder decoder = new TestLineFrameChannelDecoder(10);

        Buffer buffer = Buffer.alloc("hellohellohello");
        List<Buffer> frames = decoder.decodes(buffer);

        try {
            assertThat(frames).isEmpty();
        } finally {
            frames.forEach(Buffer::release);
            buffer.release();
        }
    }

    @Test
    void shouldReturnFrame_whenFrameExceedsMaxLength_withDelimiterAndCRLF() {
        TestLineFrameChannelDecoder decoder = new TestLineFrameChannelDecoder(10);

        Buffer buffer = Buffer.alloc("hellohellohello\r\nhello\r\n");
        List<Buffer> frames = decoder.decodes(buffer);

        try {
            assertThat(frames.getFirst().toString()).isEqualTo("hello");
        } finally {
            frames.forEach(Buffer::release);
            buffer.release();
        }
    }

    @Test
    void shouldReturnFrame_whenStripDelimiter() {
        TestLineFrameChannelDecoder decoder = new TestLineFrameChannelDecoder(10, false);

        Buffer buffer = Buffer.alloc("hello\r\n");
        List<Buffer> frames = decoder.decodes(buffer);

        try {
            assertThat(frames).hasSize(1);
            assertThat(frames.getFirst().toString()).isEqualTo("hello\r\n");
        } finally {
            frames.forEach(Buffer::release);
            buffer.release();
        }
    }

    @Test
    void shouldReturnEmptyFrame_whenFrameIsEmpty() {
        TestLineFrameChannelDecoder decoder = new TestLineFrameChannelDecoder(10);

        Buffer buffer = Buffer.alloc("\nabc\n");
        List<Buffer> frames = decoder.decodes(buffer);

        try {
            assertThat(frames.getFirst().toString()).isEqualTo("");
            assertThat(frames.get(1).toString()).isEqualTo("abc");
        } finally {
            frames.forEach(Buffer::release);
            buffer.release();
        }
    }

    @Test
    void merge_keeping_buffer_test() {
        TestLineFrameChannelDecoder decoder = new TestLineFrameChannelDecoder(10);


    }

    static class TestLineFrameChannelDecoder extends LineFrameChannelDecoder {

        public TestLineFrameChannelDecoder(int maxLength) {
            super(maxLength);
        }

        public TestLineFrameChannelDecoder(int maxLength, boolean stripDelimiter) {
            super(maxLength, stripDelimiter);
        }

        List<Buffer> decodes(Buffer buffer) {
            List<Buffer> buffers = new LinkedList<>();
            while (buffer.isReadable()) {
                Buffer decode = decode(new InMemoryNetChannel(), buffer);
                if (decode != null) {
                    buffers.add(decode);
                }
            }

            return buffers;
        }

        @Override
        protected @Nullable Buffer decode(@NonNull NetChannel channel, @NonNull Buffer buffer) {
            return super.decode(channel, buffer);
        }
    }
}