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

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.channel.ChannelInBoundHandler;
import org.traffichunter.titan.core.channel.ChannelInBoundHandlerChain;
import org.traffichunter.titan.core.channel.NetChannel;
import org.traffichunter.titan.core.util.buffer.Buffer;

/**
 * Inbound channel handler that turns a byte stream into decoded frame buffers.
 *
 * <p>The decoder keeps unread bytes between read events so subclasses can return
 * {@code null} until a full frame is available.</p>
 *
 * @author yun
 */
@Slf4j
public abstract class ChannelDecoder implements ChannelInBoundHandler {

    /**
     * Combines a previously retained buffer with newly received bytes.
     */
    public static final KeepingBuffer EXPANDING_AFTER_COPY_BUFFER = ((keepBuffer, in) -> {
        final Buffer newBuffer = Buffer.alloc(keepBuffer.length() + in.length());
        boolean isExpanding = false;
        try {
            newBuffer.accumulateBuffer(keepBuffer);
            newBuffer.accumulateBuffer(in);
            isExpanding = true;
            return newBuffer;
        } finally {
            if(!isExpanding) {
                newBuffer.release();
            }
            keepBuffer.release();
            in.release();
        }
    });

    private @Nullable Buffer keepingBuffer;

    @Override
    public void sparkChannelRead(NetChannel channel, Buffer buffer, ChannelInBoundHandlerChain chain) {

        if(keepingBuffer == null) {
            keepingBuffer = buffer;
        } else {
            keepingBuffer = EXPANDING_AFTER_COPY_BUFFER.keep(keepingBuffer, buffer);
        }

        while (keepingBuffer.isReadable()) {
            int beforeReaderIndex = keepingBuffer.byteBuf().readerIndex();

            Buffer decode = decode(channel, keepingBuffer);
            if (decode != null) {
                chain.sparkChannelRead(channel, decode);
            }

            int afterReaderIndex = keepingBuffer.byteBuf().readerIndex();
            if (afterReaderIndex == beforeReaderIndex) {
                break;
            }
        }

        if (!keepingBuffer.isReadable()) {
            keepingBuffer.release();
            keepingBuffer = null;
        }
    }

    /**
     * Attempts to decode one frame from the readable bytes in the buffer.
     *
     * @return a decoded frame, or {@code null} when more bytes are required
     */
    protected abstract @Nullable Buffer decode(NetChannel channel, Buffer buffer);

    /**
     * Strategy for carrying unread bytes across inbound read events.
     */
    public interface KeepingBuffer {

        /**
         * Returns a buffer containing the previously kept bytes and the new input.
         */
        Buffer keep(Buffer keepBuffer, Buffer in);
    }
}
