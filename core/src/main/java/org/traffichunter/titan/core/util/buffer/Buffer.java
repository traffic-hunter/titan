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
import org.traffichunter.titan.core.util.Clearable;

/**
 * Reference-counted byte buffer used inside Titan codecs and transports.
 *
 * <p>{@code Buffer} wraps Netty's {@link ByteBuf}, so other Titan code can use buffers without
 * calling Netty APIs directly. It uses separate reader and writer indexes:
 * read operations consume or inspect readable bytes, and {@code accumulate*} operations append
 * bytes at the writer index.</p>
 *
 * <p>Every buffer returned by an allocator owns one reference to its underlying {@link ByteBuf}.
 * The owner must either call {@link #release()} exactly once or transfer that reference to an API
 * whose contract takes ownership. Passing a buffer to a borrowing API does not transfer that
 * responsibility.</p>
 *
 * <p>Use {@link #heap()} for short-lived codec and protocol processing. Use {@link #direct()} only
 * where native I/O benefits from direct memory, such as socket reads and TLS packet processing.
 * Messages stored by queues and fanout use byte arrays rather than {@code Buffer}, so their
 * lifetime is managed by the JVM.</p>
 *
 * <p>Non-retained slices share both storage and reference count with their parent. Use retained
 * slice operations only when a view must outlive the current call, and release that retained
 * reference when it is no longer needed.</p>
 *
 * @author yungwang-o
 */
public interface Buffer extends Clearable {

    /**
     * Returns the shared pooled allocator for short-lived heap buffers.
     *
     * <p>Heap buffers remain reference-counted and must be released even though their storage is
     * located in JVM heap memory.</p>
     */
    static BufferAllocator heap() {
        return BufferAllocators.HEAP;
    }

    /**
     * Returns the shared pooled allocator for native transport buffers.
     *
     * <p>Prefer this allocator at socket and TLS boundaries. Application state and queued message
     * payloads should not retain direct buffers.</p>
     */
    static BufferAllocator direct() {
        return BufferAllocators.DIRECT;
    }

    /**
     * Wraps an existing {@link ByteBuf} without retaining it.
     *
     * <p>The returned wrapper represents the reference already owned by the supplied buffer. The
     * caller must not release both objects as if they owned independent references.</p>
     */
    static Buffer buffer(final ByteBuf buffer) {
        return new InternalBuffer(buffer);
    }

    /**
     * Returns a shared NIO view over the readable bytes of this buffer.
     *
     * <p>The view has no independent lifetime and must not be used after this buffer is released.</p>
     */
    ByteBuffer byteBuffer();

    /**
     * Exposes a borrowed reference to the underlying Netty buffer for channel I/O and codec
     * internals.
     *
     * <p>Calling this method does not retain the returned buffer.</p>
     */
    ByteBuf byteBuf();

    /**
     * Releases the reference represented by this buffer.
     */
    void release();

    /**
     * Adds one reference to the underlying storage.
     *
     * <p>The returned buffer must eventually be released independently from the original owner.</p>
     */
    Buffer retain();

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

    /**
     * Appends another buffer's readable bytes to this buffer.
     */
    @CanIgnoreReturnValue
    Buffer accumulateBuffer(Buffer buffer);

    @CanIgnoreReturnValue
    Buffer accumulateBuffer(Buffer buffer, int offset, int length);

    default boolean hasRemaining() {
        return length() > 0;
    }

    /**
     * Creates an owned copy with independent storage and reference count.
     */
    @CanIgnoreReturnValue
    Buffer copy();

    /**
     * Creates a non-retained slice sharing storage and reference count with this buffer.
     *
     * <p>The slice must not outlive this buffer.</p>
     */
    @CanIgnoreReturnValue
    Buffer slice();

    /**
     * Creates a retained slice with an independently releasable reference to shared storage.
     */
    @CanIgnoreReturnValue
    Buffer retainSlice();

    @CanIgnoreReturnValue
    Buffer slice(int start, int length);

    @CanIgnoreReturnValue
    Buffer retainSlice(int start, int length);

    /**
     * slice(readerIndex, length)
     * @param length length of slice
     * @return new buffer
     */
    @CanIgnoreReturnValue
    Buffer readSlice(int length);

    @CanIgnoreReturnValue
    Buffer readRetainedSlice(int length);

    @CanIgnoreReturnValue
    Buffer skipBytes(int length);

    @CanIgnoreReturnValue
    Buffer expand(int size);

    /**
     * @return {@code true} if and only if {@code (this.writerIndex - this.readerIndex)} is greater than {@code 0}.
     */
    boolean isReadable();

    /**
     * @return {@code true} if and only if {@code (this.capacity - this.writerIndex)} is greater than {@code 0}.
     */
    boolean isWritable();

    boolean isWriteable(int size);

    /**
     * @return The maximum allowed capacity of this buffer. This value provides an upper bound on capacity().
     */
    int maxCapacity();

    /**
     * @return The number of bytes (octets) this buffer can contain.
     */
    int capacity();

    int indexOf(int fromIndex, int toIndex, char value);

    int indexOf(int fromIndex, int toIndex, byte value);

    /**
     * @return Number of readable bytes {@code (writerIndex - readerIndex)}
     */
    int length();

    String toString();

    String toString(Charset charset);

    @Deprecated
    boolean canAllocate(int size);
}
