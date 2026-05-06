/*
The MIT License

Copyright (c) 2025 traffic-hunter

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
*/
package org.traffichunter.titan.core.util.buffer;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.util.Clearable;

/**
 * Accumulates incoming buffers across multiple read events.
 *
 * <p>Codecs use this when protocol frames can be split across multiple socket reads. The
 * accumulator owns an internal buffer and enforces a maximum accumulated size so malformed or
 * incomplete input cannot grow memory without bound.</p>
 *
 * @author yun
 */
@NullMarked
public final class BufferAccumulator implements Clearable {

    private @Nullable Buffer accumulator;
    private final int maxAccumulatedSize;

    public BufferAccumulator() {
        this(BufferUtils.DEFAULT_MAX_CAPACITY);
    }

    public BufferAccumulator(int maxAccumulatedSize) {
        this.maxAccumulatedSize = maxAccumulatedSize;
        this.accumulator = Buffer.alloc(BufferUtils.DEFAULT_INITIAL_CAPACITY, maxAccumulatedSize);
    }

    /**
     * Accumulates the given buffer into the internal buffer.
     *
     * @param buffer the buffer to accumulate
     * @throws IllegalStateException if accumulated size exceeds max capacity
     */
    public void accumulate(Buffer buffer) {
        if (!buffer.hasRemaining()) {
            return;
        }

        int incomingLength = buffer.length();
        int currentLength = accumulator.length();

        if (currentLength + incomingLength > maxAccumulatedSize) {
            throw new IllegalStateException(
                "Buffer accumulation exceeds max capacity: " +
                (currentLength + incomingLength) + " > " + maxAccumulatedSize
            );
        }

        accumulator.accumulateBuffer(buffer);
    }

    /**
     * Returns the accumulated buffer without removing it.
     *
     * @return the accumulated buffer
     */
    public @Nullable Buffer get() {
        return accumulator;
    }

    /**
     * Returns the number of accumulated bytes.
     *
     * @return number of bytes in the accumulator
     */
    public int size() {
        if(accumulator == null) {
            return -1;
        }

        return accumulator.length();
    }

    /**
     * Checks if the accumulator has any data.
     *
     * @return true if accumulator has data
     */
    public boolean hasData() {
        if(accumulator == null) {
            throw new IllegalStateException("Accumulator is empty");
        }
        return accumulator.hasRemaining();
    }

    /**
     * Checks if the accumulator is empty.
     *
     * @return true if accumulator is empty
     */
    public boolean isEmpty() {
        return !hasData();
    }

    /**
     * Reads and removes the specified number of bytes from the accumulator.
     * Returns a slice of the accumulated data.
     *
     * @param length number of bytes to read
     * @return a new buffer containing the requested bytes
     * @throws IllegalArgumentException if length exceeds available bytes
     */
    public Buffer read(int length) {
        if(accumulator == null) {
            throw new IllegalStateException("Accumulator is empty");
        }
        if (length > accumulator.length()) {
            throw new IllegalArgumentException(
                "Cannot read " + length + " bytes, only " + accumulator.length() + " available"
            );
        }

        return accumulator.readSlice(length);
    }

    /**
     * Reads and removes all accumulated data.
     *
     * @return a buffer containing all accumulated data
     */
    public Buffer readAll() {
        if(accumulator == null) {
            throw new IllegalStateException("Accumulator is empty");
        }
        Buffer result = accumulator.slice();
        clear();
        return result;
    }

    /**
     * Clears the accumulator and resets it to initial state.
     */
    @Override
    public void clear() {
        if (accumulator != null) {
            accumulator.release();
        }
        accumulator = Buffer.alloc(BufferUtils.DEFAULT_INITIAL_CAPACITY, maxAccumulatedSize);
    }

    /**
     * Releases resources held by the accumulator.
     */
    public void release() {
        if (accumulator != null) {
            accumulator.release();
            accumulator = null;
        }
    }
}
