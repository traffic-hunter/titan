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

import io.netty.buffer.ByteBuf;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.traffichunter.titan.core.util.Assert;

/**
 *
 * reference: vertx
 *
 * @author yungwang-o
 */
@Slf4j
class InternalBuffer implements Buffer {

    private ByteBuf buf;

    public InternalBuffer() {
        this(0);
    }

    public InternalBuffer(final String src) {
        this(src.getBytes(StandardCharsets.UTF_8));
    }

    public InternalBuffer(final String src, final Charset charset) {
        this(src.getBytes(charset));
    }

    public InternalBuffer(final int initialCapacity) {
        this.buf = AutoManagedByteBufAllocator.DEFAULT.directBuffer(initialCapacity);
    }

    public InternalBuffer(final byte[] bytes) {
        this.buf = AutoManagedByteBufAllocator.DEFAULT.directBuffer(bytes.length).writeBytes(bytes);
    }

    public InternalBuffer(final ByteBuf buf) {
        this.buf = buf;
    }

    @Override
    public ByteBuffer byteBuffer() {
        return Objects.requireNonNull(buf.nioBuffer(), "Buffer is null!!");
    }

    @Override
    public ByteBuf byteBuf() {
        return Objects.requireNonNull(buf, "Buffer is null!!");
    }

    @Override
    public void release() {
        if(buf.refCnt() == 0) {
            log.warn("Buffer has been released!");
            return;
        }

        boolean isRelease = buf.release();
        Assert.checkState(isRelease, "Buffer not fully released");
    }

    @Override
    public Buffer retain() {
        ByteBuf retain = buf.retain();
        return retain == buf ? this : new InternalBuffer(retain);
    }

    @Override
    public byte getByte(final int idx) {
        return buf.getByte(idx);
    }

    @Override
    public byte[] getBytes() {
        final byte[] array = new byte[this.length()];
        buf.getBytes(0, array);
        return array;
    }

    @Override
    public byte[] getBytes(final int idx, final byte[] dst) {
        buf.getBytes(idx, dst);
        return dst;
    }

    @Override
    public byte[] getBytes(final int start, final int length) {
        Assert.checkArgument(length > 0,"invalid range: length <= 0");
        final byte[] array = new byte[length];
        buf.getBytes(start, array, 0, length);
        return array;
    }

    @Override
    public short getUnsignedByte(final int idx) {
        return buf.getUnsignedByte(idx);
    }

    @Override
    public boolean getBoolean(final int idx) {
        return buf.getBoolean(idx);
    }

    @Override
    public short getShort(final int idx) {
        return buf.getShort(idx);
    }

    @Override
    public int getUnsignedShort(final int idx) {
        return buf.getUnsignedShort(idx);
    }

    @Override
    public int getUnsignedShortLE(final int idx) {
        return buf.getUnsignedShortLE(idx);
    }

    @Override
    public int getInt(final int idx) {
        return buf.getInt(idx);
    }

    @Override
    public int getIntLE(final int idx) {
        return buf.getIntLE(idx);
    }

    @Override
    public long getUnsignedInt(final int idx) {
        return buf.getUnsignedInt(idx);
    }

    @Override
    public long getUnsignedIntLE(final int idx) {
        return buf.getUnsignedIntLE(idx);
    }

    @Override
    public long getLong(final int idx) {
        return buf.getLong(idx);
    }

    @Override
    public int getMedium(final int idx) {
        return buf.getMedium(idx);
    }

    @Override
    public int getMediumLE(final int idx) {
        return buf.getMediumLE(idx);
    }

    @Override
    public int getUnsignedMedium(final int idx) {
        return buf.getUnsignedMedium(idx);
    }

    @Override
    public int getUnsignedMediumLE(final int idx) {
        return buf.getUnsignedMediumLE(idx);
    }

    @Override
    public String getString(final int start, final int length, final Charset charset) {
        final byte[] bytes = getBytes(start, length);
        return new String(bytes, charset);
    }

    @Override
    public Buffer getBuffer(final int start, final int length) {
        final byte[] bytes = getBytes(start, length);
        return new InternalBuffer(bytes);
    }

    @Override
    public Buffer setByte(final int idx, final byte value) {
        buf.setByte(idx, value);
        return this;
    }

    @Override
    public Buffer setUnsignedByte(final int idx, final short value) {
        buf.setByte(idx, value);
        return this;
    }

    @Override
    public Buffer setBytes(final int idx, final ByteBuffer value) {
        buf.setBytes(idx, value);
        return this;
    }

    @Override
    public Buffer setBytes(final int idx, final byte[] value) {
        buf.setBytes(idx, value);
        return this;
    }

    @Override
    public Buffer setBytes(final int idx, final byte[] value, final int offset, final int length) {
        buf.setBytes(idx, value, offset, length);
        return this;
    }

    @Override
    public Buffer setBoolean(final int idx, final boolean value) {
        buf.setBoolean(idx, value);
        return this;
    }

    @Override
    public Buffer setShort(final int idx, final short value) {
        buf.setShort(idx, value);
        return this;
    }

    @Override
    public Buffer setShortLE(final int idx, final short value) {
        buf.setShortLE(idx, value);
        return this;
    }

    @Override
    public Buffer setInt(final int idx, final int value) {
        buf.setInt(idx, value);
        return this;
    }

    @Override
    public Buffer setIntLE(final int idx, final int value) {
        buf.setIntLE(idx, value);
        return this;
    }

    @Override
    public Buffer setLong(final int idx, final long value) {
        buf.setLong(idx, value);
        return this;
    }

    @Override
    public Buffer setLongLE(final int idx, final long value) {
        buf.setLongLE(idx, value);
        return this;
    }

    @Override
    public Buffer setBuffer(final int idx, final Buffer buffer) {
        InternalBuffer internal = (InternalBuffer) buffer;
        ByteBuf buf = internal.buf;
        this.buf.setBytes(idx, buf, buf.readerIndex(), buf.readableBytes());
        return this;
    }

    @Override
    public Buffer setBuffer(final int idx, final Buffer buffer, final int offset, final int length) {
        InternalBuffer internal = (InternalBuffer) buffer;
        ByteBuf buf = internal.buf;
        this.buf.setBytes(idx, buf, buf.readerIndex() + offset, length);
        return this;
    }

    @Override
    public Buffer accumulateByte(final byte value) {
        buf.writeByte(value);
        return this;
    }

    @Override
    public Buffer accumulateUnsignedByte(final short value) {
        buf.writeByte(value);
        return null;
    }

    @Override
    public Buffer accumulateBoolean(final boolean value) {
        buf.writeBoolean(value);
        return this;
    }

    @Override
    public Buffer accumulateBytes(final byte[] value) {
        buf.writeBytes(value);
        return this;
    }

    @Override
    public Buffer accumulateBytes(final byte[] value, final int offset, final int length) {
        buf.writeBytes(value, offset, length);
        return this;
    }

    @Override
    public Buffer accumulateShort(final short value) {
        buf.writeShort(value);
        return this;
    }

    @Override
    public Buffer accumulateUnsignedShort(final int value) {
        buf.writeShort(value);
        return this;
    }

    @Override
    public Buffer accumulateShortLE(final short value) {
        buf.writeShortLE(value);
        return this;
    }

    @Override
    public Buffer accumulateUnsignedShortLE(final int value) {
        buf.writeShortLE(value);
        return this;
    }

    @Override
    public Buffer accumulateInt(final int value) {
        buf.writeInt(value);
        return this;
    }

    @Override
    public Buffer accumulateUnsignedInt(final long value) {
        buf.writeInt((int) value);
        return this;
    }

    @Override
    public Buffer accumulateIntLE(final int value) {
        buf.writeIntLE(value);
        return this;
    }

    @Override
    public Buffer accumulateUnsignedIntLE(final long value) {
        buf.writeIntLE((int) value);
        return this;
    }

    @Override
    public Buffer accumulateLong(final long value) {
        buf.writeLong(value);
        return this;
    }

    @Override
    public Buffer accumulateLongLE(final long value) {
        buf.writeLongLE(value);
        return this;
    }

    @Override
    public Buffer accumulateMedium(final int value) {
        buf.writeMedium(value);
        return this;
    }

    @Override
    public Buffer accumulateMediumLE(final int value) {
        buf.writeMediumLE(value);
        return this;
    }

    @Override
    public Buffer accumulateString(final String str) {
        accumulateString(str, StandardCharsets.UTF_8);
        return this;
    }

    @Override
    public Buffer accumulateString(final String str, final Charset charset) {
        byte[] bytes = str.getBytes(charset);
        ensureBufferAccumulation(bytes.length);
        buf.writeBytes(bytes);
        return this;
    }

    @Override
    public Buffer accumulateBuffer(final Buffer buffer) {
        InternalBuffer internal = (InternalBuffer) buffer;
        ByteBuf buf = internal.buf;
        this.buf.writeBytes(buf, buf.readerIndex(), buf.readableBytes());
        return this;
    }

    @Override
    public Buffer accumulateBuffer(final Buffer buffer, final int offset, final int length) {
        InternalBuffer internal = (InternalBuffer) buffer;
        ByteBuf buf = internal.buf;
        int start = buf.readerIndex() + offset;
        this.buf.writeBytes(buf, start, length);
        return this;
    }

    @Override
    public int length() {
        return buf.writerIndex();
    }

    @Override
    public String toString() {
        return buf.toString(StandardCharsets.UTF_8);
    }

    @Override
    public String toString(final Charset charset) {
        return buf.toString(charset);
    }

    @Override
    public void clear() {
        buf.clear();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof InternalBuffer that)) {
            return false;
        }
        return Objects.equals(buf, that.buf);
    }

    @Override
    public int hashCode() {
        if(buf == null) {
            return 0;
        }

        return buf.hashCode();
    }

    private void ensureBufferAccumulation(final int length) {
        final int curBufIndex = buf.writerIndex() + length;
        if (curBufIndex > buf.maxCapacity()) {
            reAlloc(curBufIndex);
        }
    }

    private void reAlloc(final int capacity) {
        ByteBuf temp = buf.alloc().directBuffer(capacity);
        temp.writeBytes(buf);
        buf = temp;
    }
}
