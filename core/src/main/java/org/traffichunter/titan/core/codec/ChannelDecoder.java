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

            Buffer frame = decode(channel, pending);
            if (frame != null) {
                chain.sparkChannelRead(channel, frame);
            }

            // decode(), or a handler it triggers, may close the channel (for example a
            // DISCONNECT frame). Closing releases and clears the retained buffer through
            // close(), leaving `pending` as a dangling reference. Stop here instead of
            // reading its reader index or releasing it a second time.
            if (mergeBuffer != pending) {
                return;
            }

            int afterReaderIndex = pending.byteBuf().readerIndex();
            if (afterReaderIndex == beforeReaderIndex) {
                // No bytes were consumed this iteration. When a frame was still produced the
                // decoder violated its contract: the same bytes would be decoded again on the
                // next read, emitting duplicate frames forever. Fail fast instead of looping.
                if (frame != null) {
                    throw new ChannelDecoderException(
                            getClass().getSimpleName()
                                    + ".decode() produced a frame without consuming any bytes");
                }
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
