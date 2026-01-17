package org.traffichunter.titan.core.test.implementation.codec;

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.traffichunter.titan.core.codec.LineFrameChannelDecoder;
import org.traffichunter.titan.core.util.buffer.Buffer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author yun
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class LineFrameChannelDecoderTest {

    @Test
    void shouldReturnFrame_whenDelimiterIsCRLF() {
        TestLineFrameChannelDecoder decoder = new TestLineFrameChannelDecoder(10);

        Buffer buffer = Buffer.alloc("hello\r\nhello\r\n");

        try {
            while (buffer.isReadable()) {
                Buffer frame = decoder.decode(buffer);

                assertNotNull(frame);
                assertEquals("hello", frame.toString());
            }

        } finally {
            buffer.release();
        }
    }

    @Test
    void shouldReturnFrame_whenDelimiterIsLF() {
        TestLineFrameChannelDecoder decoder = new TestLineFrameChannelDecoder(10);

        Buffer buffer = Buffer.alloc("hello\nhello\n");

        try {
            while (buffer.isReadable()) {
                Buffer frame = decoder.decode(buffer);

                assertNotNull(frame);
                assertEquals("hello", frame.toString());
            }

        } finally {
            buffer.release();
        }
    }

    @Test
    void shouldReturnNull_whenDelimiterNotFound() {
        TestLineFrameChannelDecoder decoder = new TestLineFrameChannelDecoder(10);

        Buffer buffer = Buffer.alloc("hello");

        try {
            Buffer frame = decoder.decode(buffer);

            assertNull(frame);

        } finally {
            buffer.release();
        }
    }

    @Test
    void shouldReturnNull_whenFrameExceedsMaxLength_withDelimiter() {
        TestLineFrameChannelDecoder decoder = new TestLineFrameChannelDecoder(10);

        Buffer buffer = Buffer.alloc("hellohellohello\r\n");

        try {
            while (buffer.isReadable()) {
                Buffer frame = decoder.decode(buffer);

                assertNull(frame);
            }

        } finally {
            buffer.release();
        }
    }

    @Test
    void shouldReturnNull_whenFrameExceedsMaxLength_withoutDelimiter() {
        TestLineFrameChannelDecoder decoder = new TestLineFrameChannelDecoder(10);

        Buffer buffer = Buffer.alloc("hellohellohello");

        try {
            while (buffer.isReadable()) {
                Buffer frame = decoder.decode(buffer);

                assertNull(frame);
            }

        } finally {
            buffer.release();
        }
    }

    @Test
    void shouldReturnFrame_whenFrameExceedsMaxLength_withDelimiterAndCRLF() {
        TestLineFrameChannelDecoder decoder = new TestLineFrameChannelDecoder(10);

        Buffer buffer = Buffer.alloc("hellohellohello\r\nhello\r\n");

        try {
            for (int i = 0; buffer.isReadable(); i++) {
                Buffer frame = decoder.decode(buffer);

                if (i == 0) {
                    assertNull(frame);
                } else {
                    assertNotNull(frame);
                    assertEquals("hello", frame.toString());
                }
            }

        } finally {
            buffer.release();
        }
    }

    @Test
    void shouldReturnFrame_whenStripDelimiter() {
        TestLineFrameChannelDecoder decoder = new TestLineFrameChannelDecoder(10, false);

        Buffer buffer = Buffer.alloc("hello\r\n");

        try {
            Buffer frame = decoder.decode(buffer);

            assertNotNull(frame);
            assertEquals("hello\r\n", frame.toString());
        } finally {
            buffer.release();
        }
    }

    @Test
    void shouldReturnEmptyFrame_whenFrameIsEmpty() {
        TestLineFrameChannelDecoder decoder = new TestLineFrameChannelDecoder(10);

        Buffer buffer = Buffer.alloc("\nabc\n");

        try {
            Buffer frame = decoder.decode(buffer);

            assertNotNull(frame);
            assertEquals("", frame.toString());
        } finally {
            buffer.release();
        }
    }

    static class TestLineFrameChannelDecoder extends LineFrameChannelDecoder {

        public TestLineFrameChannelDecoder(int maxLength) {
            super(maxLength);
        }

        public TestLineFrameChannelDecoder(int maxLength, boolean stripDelimiter) {
            super(maxLength, stripDelimiter);
        }

        @Override
        protected Buffer decode(@NonNull Buffer buffer) {
            return super.decode(buffer);
        }
    }
}