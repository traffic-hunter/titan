/*
 * Copyright 2025 traffic-hunter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traffichunter.titan.core.codec;

import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.channel.NetChannel;
import org.traffichunter.titan.core.util.Assert;
import org.traffichunter.titan.core.util.buffer.Buffer;

/**
 * Decodes inbound bytes into frames delimited by {@code \n} or {@code \r\n}.
 *
 * <p>Frames longer than {@code maxLength} are discarded until the next line
 * delimiter is found.</p>
 *
 * @author yun
 */
public class LineFrameChannelDecoder extends ChannelDecoder {

    private static final byte LF = '\n';
    private static final byte CR = '\r';

    private final int maxLength;
    private final boolean stripDelimiter;

    private boolean reSync;

    /**
     * Creates a decoder with a 1024-byte maximum frame length.
     */
    public LineFrameChannelDecoder() {
        this(1024);
    }

    /**
     * Creates a decoder that strips line delimiters from decoded frames.
     */
    public LineFrameChannelDecoder(int maxLength) {
        this(maxLength, true);
    }

    /**
     * Creates a decoder with the given maximum frame length and delimiter behavior.
     */
    public LineFrameChannelDecoder(int maxLength, boolean stripDelimiter) {
        Assert.checkArgument(maxLength > 0, "maxLength must be greater than 0");

        this.maxLength = maxLength;
        this.stripDelimiter = stripDelimiter;
    }

    @Override
    protected @Nullable Buffer decode(NetChannel channel, Buffer buffer) {
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
