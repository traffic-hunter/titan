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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public abstract class ChannelDecoder implements ChannelInBoundHandler, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ChannelDecoder.class);

    /**
     * Combines a previously retained buffer with newly received bytes.
     */
    public static final MergeBuffer MERGE_BUFFER = ((mergeBuffer, in) -> {
        final Buffer newBuffer = Buffer.heap().alloc(mergeBuffer.length() + in.length());
        boolean isExpanding = false;
        try {
            newBuffer.accumulateBuffer(mergeBuffer);
            newBuffer.accumulateBuffer(in);
            isExpanding = true;
            return newBuffer;
        } finally {
            if(!isExpanding) {
                newBuffer.release();
            }
            mergeBuffer.release();
            in.release();
        }
    });

    private @Nullable Buffer mergeBuffer;

    @Override
    public void sparkChannelRead(NetChannel channel, Buffer buffer, ChannelInBoundHandlerChain chain) {
        if(mergeBuffer == null) {
            mergeBuffer = buffer;
        } else {
            mergeBuffer = MERGE_BUFFER.merge(mergeBuffer, buffer);
        }

        relayingDecode(channel, chain);
    }

    /**
     * Attempts to decode the bytes currently retained by this decoder.
     *
     * <p>This is useful when decoding previously stopped without consuming input and an
     * asynchronous prerequisite has since completed.</p>
     */
    protected final void relayingDecode(NetChannel channel, ChannelInBoundHandlerChain chain) {
        Buffer pending = mergeBuffer;
        if (pending == null) {
            return;
        }

        while (pending.isReadable()) {
            int beforeReaderIndex = pending.byteBuf().readerIndex();

            Buffer decode = decode(channel, pending);
            if (decode != null) {
                chain.sparkChannelRead(channel, decode);
            }

            int afterReaderIndex = pending.byteBuf().readerIndex();
            if (afterReaderIndex == beforeReaderIndex) {
                break;
            }
        }

        if (!pending.isReadable()) {
            pending.release();
            mergeBuffer = null;
        }
    }

    /**
     * Releases bytes retained while waiting for a complete frame.
     *
     * <p>Channel lifecycle code invokes this method on the owning event-loop thread.</p>
     */
    @Override
    public void close() {
        Buffer pending = mergeBuffer;
        mergeBuffer = null;
        if (pending != null && pending.byteBuf().refCnt() > 0) {
            pending.release();
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
    public interface MergeBuffer {

        /**
         * Returns a buffer containing the previously kept bytes and the new input.
         */
        Buffer merge(Buffer keepBuffer, Buffer in);
    }
}
