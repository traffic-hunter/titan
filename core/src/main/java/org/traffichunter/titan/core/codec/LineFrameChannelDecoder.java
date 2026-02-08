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
package org.traffichunter.titan.core.codec;

import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.util.Assert;
import org.traffichunter.titan.core.util.buffer.Buffer;

/**
 * @author yun
 */
public class LineFrameChannelDecoder extends ChannelDecoder {

    private static final byte LF = '\n';
    private static final byte CR = '\r';

    private final int maxLength;
    private final boolean stripDelimiter;

    private boolean reSync;

    public LineFrameChannelDecoder() {
        this(1024);
    }

    public LineFrameChannelDecoder(int maxLength) {
        this(maxLength, true);
    }

    public LineFrameChannelDecoder(int maxLength, boolean stripDelimiter) {
        Assert.checkArgument(maxLength > 0, "maxLength must be greater than 0");

        this.maxLength = maxLength;
        this.stripDelimiter = stripDelimiter;
    }

    @Override
    protected @Nullable Buffer decode(Buffer buffer) {
        return decode0(buffer);
    }

    private @Nullable Buffer decode0(Buffer buffer) {
        final int eol = findEol(buffer);
        int length = eol - buffer.byteBuf().readerIndex();
        if(!reSync) {
            if (eol < 0) {
                length = buffer.length();
                if (length > maxLength) {
                    reSync = true;
                }
                buffer.skipBytes(length);
                buffer.byteBuf().readerIndex(buffer.byteBuf().writerIndex());
                return null;
            }

            final int delimiterLength = buffer.getByte(eol) == CR ? 2 : 1;

            if (length > maxLength) {
                buffer.byteBuf().readerIndex(eol + delimiterLength);
                return null;
            }

            Buffer frame;
            if (stripDelimiter) {
                frame = buffer.readRetainedSlice(length);
                buffer.skipBytes(delimiterLength);
            } else {
                frame = buffer.readRetainedSlice(length + delimiterLength);
            }

            return frame;
        } else {
            if(eol < 0) {
                buffer.byteBuf().readerIndex(buffer.byteBuf().writerIndex());
                return null;
            }

            int delimiterLength = buffer.getByte(eol) == CR ? 2 : 1;
            buffer.byteBuf().readerIndex(eol + delimiterLength);
            reSync = false;
            return null;
        }
    }

    /**
     * Eol (end of line)
     * @return -1 if not found
     */
    private int findEol(final Buffer buffer) {
        final int totalLength = buffer.length();
        final int readIdx = buffer.byteBuf().readerIndex();

        int idx = buffer.indexOf(readIdx, readIdx + totalLength, LF);
        if(idx >= 0) {
            if(idx > 0 && buffer.getByte(idx - 1) == CR) {
                idx--;
            }
            return idx;
        }

        return idx;
    }
}