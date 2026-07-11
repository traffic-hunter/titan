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
package org.traffichunter.titan.core.codec.websocket;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traffichunter.titan.core.channel.NetChannel;
import org.traffichunter.titan.core.codec.ChannelDecoder;
import org.traffichunter.titan.core.util.Protocol;
import org.traffichunter.titan.core.util.buffer.Buffer;

import static org.traffichunter.titan.core.codec.websocket.WebSocketFrame.isControlFrame;
import static org.traffichunter.titan.core.codec.websocket.WebSocketFrameHeader.*;

/**
 * @author yun
 */
public class WebSocketFrameDecoder extends ChannelDecoder {

    private static final Logger log = LoggerFactory.getLogger(WebSocketFrameDecoder.class);

    private final WebSocketFrameParser parser;

    public WebSocketFrameDecoder() {
        this(Protocol.STOMP);
    }

    public WebSocketFrameDecoder(String subProtocol) {
        this(Protocol.subProtocol(subProtocol));
    }

    public WebSocketFrameDecoder(Protocol subProtocol) {
        this.parser = new WebSocketFrameParser(subProtocol);
    }

    @Override
    protected @Nullable Buffer decode(NetChannel channel, Buffer buffer) {
        try {
            WebSocketFrame websocketFrame = parser.parse(buffer);
            if (websocketFrame == null) {
                return null;
            }
            if (isControlFrame(websocketFrame.header().getOpCode())) {
                websocketFrame.payload().release();
                if (websocketFrame.header().getOpCode() == OpCode.CLOSE) {
                    channel.close();
                }
                return null;
            }

            return websocketFrame.payload();
        } catch (WebSocketFrameException e) {
            log.warn("Rejected invalid websocket frame. reason={}", e.getMessage());
            discard(buffer);
            channel.close();
            return null;
        } catch (Exception e) {
            log.error("Failed to parse websocket frame", e);
            discard(buffer);
            channel.close();
            return null;
        }
    }

    private static void discard(Buffer buffer) {
        if (buffer.isReadable()) {
            buffer.skipBytes(buffer.length());
        }
    }

    private static final class WebSocketFrameParser {

        private static final int FIN_MASK = 0x80;
        private static final int RSV_MASK = 0x70;
        private static final int OPCODE_MASK = 0x0F;
        private static final int MASK_BIT = 0x80;
        private static final int PAYLOAD_LENGTH_MASK = 0x7F;
        private static final int SHORT_PAYLOAD_LENGTH_MARKER = 126;
        private static final int CONTROL_FRAME_MAX_PAYLOAD_LENGTH = 125;

        private final Protocol subProtocol;

        private WebSocketFrameParser(Protocol subProtocol) {
            this.subProtocol = subProtocol;
        }

        @Nullable WebSocketFrame parse(Buffer buffer) {
            if (!buffer.isReadable()) {
                return null;
            }

            int readerIndex = buffer.byteBuf().readerIndex();
            int readableBytes = buffer.byteBuf().readableBytes();
            if (readableBytes < MIN_FRAME_HEADER_LENGTH) {
                return null;
            }

            short firstByte = buffer.getUnsignedByte(readerIndex);
            short secondByte = buffer.getUnsignedByte(readerIndex + 1);
            if ((firstByte & RSV_MASK) != 0) {
                throw new WebSocketFrameException("WebSocket extensions are not supported");
            }

            boolean fin = (firstByte & FIN_MASK) != 0;
            OpCode opcode = OpCode.of(firstByte & OPCODE_MASK);
            long lengthCode = secondByte & PAYLOAD_LENGTH_MASK;

            int headerLength = MIN_FRAME_HEADER_LENGTH;
            long payloadLength;
            if (lengthCode < SHORT_PAYLOAD_LENGTH_MARKER) {
                payloadLength = lengthCode;
            } else if (lengthCode == SHORT_PAYLOAD_LENGTH_MARKER) {
                if (readableBytes < headerLength + Short.BYTES) {
                    return null;
                }
                payloadLength = buffer.getUnsignedShort(readerIndex + headerLength);
                headerLength += 2;
                if (payloadLength < SHORT_PAYLOAD_LENGTH_MARKER) {
                    throw new WebSocketFrameException("Non-minimal WebSocket payload length encoding");
                }
            } else {
                if (readableBytes < headerLength + Long.BYTES) {
                    return null;
                }
                payloadLength = buffer.getLong(readerIndex + headerLength);
                headerLength += Long.BYTES;
                if (payloadLength <= 0xFFFF) {
                    throw new WebSocketFrameException("Non-minimal WebSocket payload length encoding");
                }
            }

            validate(opcode, fin, payloadLength);

            boolean isMasked = (secondByte & MASK_BIT) != 0;
            int maskingKey = 0;
            if (isMasked) {
                if (readableBytes < headerLength + Integer.BYTES) {
                    return null;
                }
                maskingKey = buffer.getInt(readerIndex + headerLength);
                headerLength += Integer.BYTES;
            }

            if (readableBytes < headerLength + payloadLength) {
                return null;
            }

            buffer.skipBytes(headerLength);
            Buffer payload = buffer.readRetainedSlice((int) payloadLength);
            if (isMasked) {
                payload = WebSocketFrameHeader.unmask(payload, maskingKey);
            }

            WebSocketFrameHeader.Builder headerBuilder = WebSocketFrameHeader.builder()
                    .op(opcode, fin)
                    .payloadLength(payloadLength);
            if (isMasked) {
                headerBuilder.masked(maskingKey);
            }
            WebSocketFrameHeader header = headerBuilder.build();

            return new WebSocketFrame(header, payload, subProtocol);
        }

        private static void validate(OpCode opcode, boolean fin, long payloadLength) {
            if (!fin || opcode == OpCode.CONTINUATION) {
                throw new WebSocketFrameException("Fragmented WebSocket messages are not supported");
            }
            if (isControlFrame(opcode) && payloadLength > CONTROL_FRAME_MAX_PAYLOAD_LENGTH) {
                throw new WebSocketFrameException("Control frame payload must be less than 126 bytes");
            }
            if (payloadLength < 0 || payloadLength > Integer.MAX_VALUE) {
                throw new WebSocketFrameException("Invalid WebSocket payload length: " + payloadLength);
            }
        }
    }
}
