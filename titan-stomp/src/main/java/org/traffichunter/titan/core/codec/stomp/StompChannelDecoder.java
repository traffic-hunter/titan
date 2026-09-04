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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.channel.ChannelInBoundHandlerChain;
import org.traffichunter.titan.core.channel.NetChannel;
import org.traffichunter.titan.core.channel.stomp.StompHandler;
import org.traffichunter.titan.core.channel.stomp.StompClientChannel;
import org.traffichunter.titan.core.channel.stomp.StompServerHandler;
import org.traffichunter.titan.core.codec.ChannelDecoder;
import org.traffichunter.titan.core.codec.TooLongFrameException;
import org.traffichunter.titan.core.util.buffer.Buffer;

import java.nio.charset.StandardCharsets;

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
            if (!(handler instanceof StompServerHandler)) {
                throw error;
            }
            String reason = error.getMessage() == null ? "STOMP frame size limit exceeded" : error.getMessage();
            log.warn("Rejected oversized STOMP frame. session={}, reason={}", stompChannel.session(), reason);
            stompChannel.send(errorFrame("Frame size limit exceeded.", reason))
                    .onSuccess(ignored -> stompChannel.close())
                    .onFailure(sendError -> {
                        log.warn("Failed to send STOMP ERROR frame before closing. session={}", stompChannel.session(), sendError);
                        stompChannel.close();
                    });
        }
    }

    @Override
    protected @Nullable Buffer decode(NetChannel channel, Buffer buffer) {
        StompFrame frame = stompParser.parse(buffer);
        if (frame == null) {
            return null;
        }

        handler.handle(frame, stompChannel);

        return frame.toBuffer();
    }

    static class StompParser {

        private static final String COLON = StompDelimiter.COLON.getString();
        private static final String CONTENT_LENGTH = "content-length";

        private final int maxFrameLength;

        private StompParser(int maxFrameLength) {
            this.maxFrameLength = maxFrameLength;
        }

        private @Nullable StompFrame parse(Buffer buffer) {
            if (!buffer.isReadable()) {
                return null;
            }

            // STOMP heartbeat can be a single LF byte without NUL.
            int readerIndex = buffer.byteBuf().readerIndex();
            if (buffer.getByte(readerIndex) == StompDelimiter.LF.getHex()) {
                buffer.skipBytes(1);
                return StompFrame.PING;
            }

            int bodyStart = findBodyStart(buffer, readerIndex);
            if (bodyStart == -1) {
                validateFrameLength(buffer.length());
                return null;
            }

            int headerEnd = headerEnd(buffer, bodyStart);
            String head = new String(
                    buffer.getBytes(readerIndex, headerEnd - readerIndex),
                    StandardCharsets.UTF_8
            );
            String[] lines = head.split("\\r?\\n");
            if (lines.length == 0 || lines[0].isBlank()) {
                return StompFrame.ERR_STOMP_FRAME;
            }

            StompCommand stompCommand = StompCommand.valueOf(lines[0].toUpperCase());
            int contentLength = -1;
            StompHeaders headers = new StompHeaders(StompVersion.STOMP_1_2);
            for (int i = 1; i < lines.length; i++) {
                String[] keyValue = lines[i].split(COLON, 2);
                if (keyValue.length != 2) {
                    return StompFrame.ERR_STOMP_FRAME;
                }

                String key = keyValue[0].trim();
                String value = keyValue[1].trim();
                if (key.equals(CONTENT_LENGTH)) {
                    contentLength = Integer.parseInt(value);
                    if (contentLength < 0) {
                        return StompFrame.ERR_STOMP_FRAME;
                    }
                }
                headers.put(Elements.convertToElements(key), value);
            }

            int frameEnd;
            int bodyLength;
            if (contentLength >= 0) {
                long declaredFrameLength = (long) bodyStart - readerIndex + contentLength;
                if (declaredFrameLength > maxFrameLength) {
                    throw new TooLongFrameException(
                            "STOMP frame exceeds " + maxFrameLength + ": " + declaredFrameLength
                    );
                }
                frameEnd = Math.toIntExact((long) bodyStart + contentLength);
                if (buffer.byteBuf().writerIndex() <= frameEnd) {
                    return null;
                }
                if (buffer.getByte(frameEnd) != StompDelimiter.NUL.getHex()) {
                    int terminator = findNul(buffer, bodyStart);
                    if (terminator >= 0) {
                        buffer.skipBytes(terminator - readerIndex + 1);
                    }
                    return StompFrame.ERR_STOMP_FRAME;
                }
                bodyLength = contentLength;
            } else {
                frameEnd = findNul(buffer, bodyStart);
                if (frameEnd == -1) {
                    validateFrameLength(buffer.length());
                    return null;
                }
                validateFrameLength(frameEnd - readerIndex);
                bodyLength = frameEnd - bodyStart;
            }

            byte[] body = bodyLength == 0
                    ? new byte[0]
                    : buffer.getBytes(bodyStart, bodyLength);
            buffer.skipBytes(frameEnd - readerIndex + 1);
            return StompFrame.create(headers, stompCommand, body);
        }

        private void validateFrameLength(int frameLength) {
            if (frameLength > maxFrameLength) {
                throw new TooLongFrameException(
                        "STOMP frame exceeds " + maxFrameLength + ": " + frameLength
                );
            }
        }

        private static int findBodyStart(Buffer buffer, int readerIndex) {
            int writerIndex = buffer.byteBuf().writerIndex();
            for (int index = readerIndex; index < writerIndex - 1; index++) {
                if (buffer.getByte(index) == StompDelimiter.LF.getHex()
                        && buffer.getByte(index + 1) == StompDelimiter.LF.getHex()) {
                    return index + 2;
                }
                if (index < writerIndex - 3
                        && buffer.getByte(index) == StompDelimiter.CR.getHex()
                        && buffer.getByte(index + 1) == StompDelimiter.LF.getHex()
                        && buffer.getByte(index + 2) == StompDelimiter.CR.getHex()
                        && buffer.getByte(index + 3) == StompDelimiter.LF.getHex()) {
                    return index + 4;
                }
            }
            return -1;
        }

        private static int headerEnd(Buffer buffer, int bodyStart) {
            return buffer.getByte(bodyStart - 2) == StompDelimiter.LF.getHex()
                    ? bodyStart - 2
                    : bodyStart - 4;
        }

        private static int findNul(Buffer buffer, int fromIndex) {
            return buffer.indexOf(
                    fromIndex,
                    buffer.byteBuf().writerIndex(),
                    StompDelimiter.NUL.getHex()
            );
        }
    }
}
