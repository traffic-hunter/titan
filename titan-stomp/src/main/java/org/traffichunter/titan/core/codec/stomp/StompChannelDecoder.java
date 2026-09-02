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
package org.traffichunter.titan.core.codec.stomp;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traffichunter.titan.core.channel.ChannelInBoundHandlerChain;
import org.traffichunter.titan.core.channel.NetChannel;
import org.traffichunter.titan.core.channel.stomp.StompClientChannel;
import org.traffichunter.titan.core.channel.stomp.StompHandler;
import org.traffichunter.titan.core.codec.ChannelDecoder;
import org.traffichunter.titan.core.codec.LineFrameChannelDecoder;
import org.traffichunter.titan.core.codec.TooLongFrameException;
import org.traffichunter.titan.core.util.buffer.Buffer;

import java.util.LinkedList;
import java.util.List;

import static org.traffichunter.titan.core.codec.stomp.StompHeaders.*;
import static org.traffichunter.titan.core.codec.stomp.StompFrame.errorFrame;

/**
 * @author yun, gkdbssla97
 */
public class StompChannelDecoder extends ChannelDecoder {

    private static final Logger log = LoggerFactory.getLogger(StompChannelDecoder.class);

    private static final int DEFAULT_MAX_LENGTH = 65536;

    private final StompParser stompParser;
    private final StompClientChannel stompChannel;
    private final StompHandler handler;

    public StompChannelDecoder(StompClientChannel stompChannel) {
        this(stompChannel, stompChannel.handler());
    }

    public StompChannelDecoder(StompClientChannel stompChannel, StompHandler handler) {
        this(DEFAULT_MAX_LENGTH, stompChannel, handler);
    }

    public StompChannelDecoder(int maxFrameLength, StompClientChannel stompChannel, StompHandler handler) {
        if (maxFrameLength <= 0) {
            throw new IllegalArgumentException("STOMP decoder limit must be greater than zero");
        }
        this.stompParser = new StompParser(maxFrameLength);
        this.stompChannel = stompChannel;
        this.handler = handler;
    }

    @Override
    public void sparkChannelRead(NetChannel channel, Buffer buffer, ChannelInBoundHandlerChain chain) {
        try {
            super.sparkChannelRead(channel, buffer, chain);
        } catch (TooLongFrameException error) {
            String reason = error.getMessage() == null ? "STOMP frame size limit exceeded" : error.getMessage();
            log.warn("Rejected oversized STOMP frame. session={}, reason={}",
                    stompChannel.session(), reason);
            try {
                stompChannel.send(errorFrame("Frame size limit exceeded.", reason))
                        .onSuccess(ignored -> stompChannel.close())
                        .onFailure(sendError -> {
                            log.warn("Failed to send STOMP ERROR frame before closing. session={}",
                                    stompChannel.session(), sendError);
                            stompChannel.close();
                        });
            } catch (RuntimeException sendError) {
                log.warn("Failed to send STOMP ERROR frame before closing. session={}",
                        stompChannel.session(), sendError);
                stompChannel.close();
            }
        }
    }

    @Override
    protected @Nullable Buffer decode(NetChannel channel, Buffer buffer) {
        StompFrame frame = stompParser.parse(channel, buffer);
        if (frame == null) {
            return null;
        }

        handler.handle(frame, stompChannel);

        return frame.toBuffer();
    }

    static class StompParser {

        private static final String NULL = StompDelimiter.NUL.getString();
        private static final String COLON = StompDelimiter.COLON.getString();
        private static final String CONTENT_LENGTH = "content-length";

        private final LineFrameChannelDecoderWrapper lineFrameDecoder;
        private final int maxFrameLength;

        private StompParser(int maxFrameLength) {
            this.lineFrameDecoder = new LineFrameChannelDecoderWrapper(maxFrameLength);
            this.maxFrameLength = maxFrameLength;
        }

        private @Nullable StompFrame parse(NetChannel channel, Buffer buffer) {
            if (!buffer.isReadable()) {
                return null;
            }

            // STOMP heartbeat can be a single LF byte without NUL.
            int readerIndex = buffer.byteBuf().readerIndex();
            if (buffer.getByte(readerIndex) == StompDelimiter.LF.getHex()) {
                buffer.skipBytes(1);
                return StompFrame.PING;
            }

            int frameEnd = findFrameEnd(buffer);
            if (frameEnd == -1) {
                validateFrameLength(buffer.length());
                return null;
            }

            int length = frameEnd - readerIndex;
            validateFrameLength(length);
            Buffer sliceBuffer = buffer.readSlice(length);
            buffer.skipBytes(1);

            Buffer stompFrame = Buffer.heap().alloc(sliceBuffer.length() + 1);
            List<Buffer> frames = List.of();
            try {
                stompFrame.accumulateBuffer(sliceBuffer)
                        .accumulateByte(StompDelimiter.LF.getHex());
                frames = lineFrameDecoder.decodes(channel, stompFrame);

                StompCommand stompCommand = StompCommand.valueOf(frames.getFirst().toString());

                int bodyLength = -1;
                StompHeaders headers = new StompHeaders(StompVersion.STOMP_1_2);
                for(int i = 1; i < frames.size(); i++) {
                    String header = frames.get(i).toString();
                    if(header.isBlank()) {
                        break;
                    } else {
                        String[] keyValue = header.split(COLON, 2);
                        if (keyValue.length != 2) {
                            return StompFrame.ERR_STOMP_FRAME;
                        }

                        String key = keyValue[0].trim();
                        String value = keyValue[1].trim();
                        if(key.equals(CONTENT_LENGTH)) {
                            bodyLength = Integer.parseInt(value);
                        }

                        headers.put(Elements.convertToElements(key), value);
                    }
                }

                Buffer bodyBuffer = frames.getLast();
                byte[] body = bodyBuffer.getBytes();
                if(bodyLength > -1 && bodyLength != body.length) {
                    return StompFrame.ERR_STOMP_FRAME;
                }

                return StompFrame.create(headers, stompCommand, body);
            } finally {
                stompFrame.release();
                frames.forEach(Buffer::release);
            }
        }

        private void validateFrameLength(int frameLength) {
            if (frameLength > maxFrameLength) {
                throw new TooLongFrameException(
                        "STOMP frame exceeds " + maxFrameLength + ": " + frameLength
                );
            }
        }

        private int findFrameEnd(Buffer buffer) {
            int totalLength = buffer.length();
            int readIdx = buffer.byteBuf().readerIndex();
            return buffer.indexOf(readIdx, readIdx + totalLength, NULL.charAt(0));
        }
    }

    static class LineFrameChannelDecoderWrapper extends LineFrameChannelDecoder {

        private LineFrameChannelDecoderWrapper(int maxLength) {
            super(maxLength);
        }

        List<Buffer> decodes(NetChannel channel, Buffer buffer) {
            List<Buffer> buffers = new LinkedList<>();
            try {
                while (buffer.isReadable()) {
                    Buffer decode = decode(channel, buffer);
                    if (decode != null) {
                        buffers.add(decode);
                    }
                }
                return buffers;
            } catch (RuntimeException e) {
                buffers.forEach(Buffer::release);
                throw e;
            }
        }

        @Override
        protected @Nullable Buffer decode(NetChannel channel, Buffer buffer) {
            return super.decode(channel, buffer);
        }
    }
}
