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
import org.traffichunter.titan.core.codec.ChannelDecoderException;
import org.traffichunter.titan.core.codec.TooLongFrameException;
import org.traffichunter.titan.core.codec.stomp.StompFrame.StompFrameException;
import org.traffichunter.titan.core.util.buffer.Buffer;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.traffichunter.titan.core.codec.stomp.StompHeaders.*;
import static org.traffichunter.titan.core.codec.stomp.StompFrame.errorFrame;

/**
 * Parses STOMP frames from a channel's inbound byte stream.
 *
 * <p>Incomplete frames remain buffered. The frame-length limit applies while waiting for the
 * rest of a frame as well as when parsing a complete one.</p>
 *
 * <p>Each complete frame goes to the {@link StompHandler}. Nothing is passed on to inbound
 * handlers added after this decoder.</p>
 *
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

    /**
     * Creates a decoder with a per-frame byte limit.
     *
     * @param maxFrameLength maximum frame length in bytes, excluding the final NUL delimiter
     * @param stompChannel connection associated with decoded frames
     * @param handler recipient of complete frames
     * @throws IllegalArgumentException if {@code maxFrameLength} is not positive
     */
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
        int readerIndex = buffer.byteBuf().readerIndex();
        StompFrame frame = stompParser.parse(buffer);
        if (frame == null) {
            return null;
        }

        handler.handle(frame, stompChannel);
        if (frame == StompFrame.ERR_STOMP_FRAME && buffer.byteBuf().readerIndex() == readerIndex) {
            throw new ChannelDecoderException("Invalid STOMP frame could not be skipped");
        }
        return null;
    }

    static class StompParser {

        private static final char COLON = StompDelimiter.COLON.getCharacter();
        private static final String CONTENT_LENGTH = "content-length";
        private static final byte[] LF_LF = {'\n', '\n'};
        private static final byte[] CRLF_CRLF = {'\r', '\n', '\r', '\n'};

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
            List<String> lines = splitLines(head);
            if (lines.isEmpty() || lines.getFirst().isBlank()) {
                return StompFrame.ERR_STOMP_FRAME;
            }

            StompCommand stompCommand = StompCommand.valueOf(lines.get(0).toUpperCase());
            int contentLength = -1;
            StompHeaders headers = new StompHeaders(StompVersion.STOMP_1_2);
            for (int i = 1; i < lines.size(); i++) {
                String line = lines.get(i);
                int colon = line.indexOf(COLON);
                if (colon == -1) {
                    return StompFrame.ERR_STOMP_FRAME;
                }

                String key;
                String value;
                try {
                    key = StompHeaders.decode(line.substring(0, colon).trim(), stompCommand);
                    value = StompHeaders.decode(line.substring(colon + 1).trim(), stompCommand);
                } catch (StompFrameException error) {
                    return StompFrame.ERR_STOMP_FRAME;
                }
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

        // Lines end with LF or CRLF. Empty lines at the end are dropped.
        private static List<String> splitLines(String head) {
            List<String> lines = new ArrayList<>();
            int start = 0;
            int lineFeed = head.indexOf('\n', start);
            while (lineFeed != -1) {
                boolean crlf = lineFeed > start && head.charAt(lineFeed - 1) == '\r';
                lines.add(head.substring(start, crlf ? lineFeed - 1 : lineFeed));
                start = lineFeed + 1;
                lineFeed = head.indexOf('\n', start);
            }
            lines.add(head.substring(start));

            while (!lines.isEmpty() && lines.getLast().isEmpty()) {
                lines.removeLast();
            }
            return lines;
        }

        // The headers end at the first empty line, so only positions holding an LF are checked.
        private static int findBodyStart(Buffer buffer, int readerIndex) {
            int writerIndex = buffer.byteBuf().writerIndex();
            int lineFeed = buffer.indexOf(readerIndex, writerIndex, StompDelimiter.LF.getHex());
            while (lineFeed != -1) {
                if (matches(buffer, lineFeed, writerIndex, LF_LF)) {
                    return lineFeed + LF_LF.length;
                }
                int carriageReturn = lineFeed - 1;
                if (carriageReturn >= readerIndex && matches(buffer, carriageReturn, writerIndex, CRLF_CRLF)) {
                    return carriageReturn + CRLF_CRLF.length;
                }
                lineFeed = buffer.indexOf(lineFeed + 1, writerIndex, StompDelimiter.LF.getHex());
            }
            return -1;
        }

        private static boolean matches(Buffer buffer, int index, int writerIndex, byte[] expected) {
            if (index + expected.length > writerIndex) {
                return false;
            }
            for (int i = 0; i < expected.length; i++) {
                if (buffer.getByte(index + i) != expected[i]) {
                    return false;
                }
            }
            return true;
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
