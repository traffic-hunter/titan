/*
 * The MIT License
 *
 * Copyright (c) 2025 traffic-hunter
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.traffichunter.titan.core.util.buffer;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.netty.buffer.ByteBuf;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Objects;
import org.traffichunter.titan.core.codec.Codec;

/**
 * @author yungwang-o
 */
public interface Buffer extends Clearable {

    static Buffer alloc() {
       return new InternalHeapBuffer();
    }

    static Buffer alloc(final int initialCapacity) {
        return new InternalHeapBuffer(initialCapacity);
    }

    static Buffer alloc(final String data) {
        Objects.requireNonNull(data);
        return new InternalHeapBuffer(data);
    }

    static Buffer alloc(final String data, final Charset charset) {
        Objects.requireNonNull(data); Objects.requireNonNull(charset);
        return new InternalHeapBuffer(data, charset);
    }

    static Buffer alloc(final byte[] data) {
        Objects.requireNonNull(data);
        return new InternalHeapBuffer(data);
    }

    static Buffer allocAfterDecode(final String data) {
        Objects.requireNonNull(data);
        byte[] decode = Codec.decode(data);
        return new InternalHeapBuffer(decode);
    }

    ByteBuffer byteBuffer();

    ByteBuf byteBuf();

    byte getByte(int idx);

    byte[] getBytes();

    byte[] getBytes(int idx, byte[] dst);

    byte[] getBytes(int start, int length);

    boolean getBoolean(int idx);

    short getUnsignedByte(int idx);

    short getShort(int idx);

    int getUnsignedShort(int idx);

    int getUnsignedShortLE(int idx);

    int getInt(int idx);

    long getUnsignedInt(int idx);

    int getIntLE(int idx);

    long getUnsignedIntLE(int idx);

    long getLong(int idx);

    int getMedium(int idx);

    int getMediumLE(int idx);

    int getUnsignedMedium(int idx);

    int getUnsignedMediumLE(int idx);

    String getString(int start, int length, Charset charset);

    Buffer getBuffer(int start, int length);

    @CanIgnoreReturnValue
    Buffer setByte(int idx, byte value);

    @CanIgnoreReturnValue
    Buffer setBytes(int idx, ByteBuffer value);

    @CanIgnoreReturnValue
    Buffer setBytes(int idx, byte[] value);

    @CanIgnoreReturnValue
    Buffer setBytes(int idx, byte[] value, int offset, int length);

    @CanIgnoreReturnValue
    Buffer setUnsignedByte(int idx, short value);

    @CanIgnoreReturnValue
    Buffer setBoolean(int idx, boolean value);

    @CanIgnoreReturnValue
    Buffer setShort(int idx, short value);

    @CanIgnoreReturnValue
    Buffer setShortLE(int idx, short value);

    @CanIgnoreReturnValue
    Buffer setInt(int idx, int value);

    @CanIgnoreReturnValue
    Buffer setIntLE(int idx, int value);

    @CanIgnoreReturnValue
    Buffer setLong(int idx, long value);

    @CanIgnoreReturnValue
    Buffer setLongLE(int idx, long value);

    @CanIgnoreReturnValue
    Buffer setBuffer(int idx, Buffer buffer);

    @CanIgnoreReturnValue
    Buffer setBuffer(int idx, Buffer buffer, int offset, int length);

    @CanIgnoreReturnValue
    Buffer accumulateByte(byte value);

    @CanIgnoreReturnValue
    Buffer accumulateUnsignedByte(short value);

    @CanIgnoreReturnValue
    Buffer accumulateBoolean(boolean value);

    @CanIgnoreReturnValue
    Buffer accumulateBytes(byte[] value);

    @CanIgnoreReturnValue
    Buffer accumulateBytes(byte[] value, int offset, int length);

    @CanIgnoreReturnValue
    Buffer accumulateShort(short value);

    @CanIgnoreReturnValue
    Buffer accumulateUnsignedShort(int value);

    @CanIgnoreReturnValue
    Buffer accumulateShortLE(short value);

    @CanIgnoreReturnValue
    Buffer accumulateUnsignedShortLE(int value);

    @CanIgnoreReturnValue
    Buffer accumulateInt(int value);

    @CanIgnoreReturnValue
    Buffer accumulateUnsignedInt(long value);

    @CanIgnoreReturnValue
    Buffer accumulateIntLE(int value);

    @CanIgnoreReturnValue
    Buffer accumulateUnsignedIntLE(long value);

    @CanIgnoreReturnValue
    Buffer accumulateLong(long value);

    @CanIgnoreReturnValue
    Buffer accumulateLongLE(long value);

    @CanIgnoreReturnValue
    Buffer accumulateMedium(int value);

    @CanIgnoreReturnValue
    Buffer accumulateMediumLE(int value);

    @CanIgnoreReturnValue
    Buffer accumulateString(String str);

    @CanIgnoreReturnValue
    Buffer accumulateString(String str, Charset charset);

    @CanIgnoreReturnValue
    Buffer accumulateBuffer(Buffer buffer);

    @CanIgnoreReturnValue
    Buffer accumulateBuffer(Buffer buffer, int offset, int length);

    int length();

    String toString();

    String toString(Charset charset);
}
