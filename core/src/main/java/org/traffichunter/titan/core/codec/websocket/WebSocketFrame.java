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
import org.traffichunter.titan.core.util.Protocol;
import org.traffichunter.titan.core.util.buffer.Buffer;

import static org.traffichunter.titan.core.codec.websocket.WebSocketFrameHeader.*;

/**
 * @author yun
 */
public record WebSocketFrame(
        WebSocketFrameHeader header,
        Buffer payload,
        Protocol subProtocol
) {

    public WebSocketFrame(WebSocketFrameHeader header, Buffer payload) {
        this(header, payload, Protocol.STOMP);
    }

    public WebSocketFrame(WebSocketFrameHeader header, Buffer payload, String subProtocol) {
        this(header, payload, Protocol.subProtocol(subProtocol));
    }

    /**
     * Encodes this frame into the RFC 6455 wire representation.
     *
     * <p>The returned buffer owns independent storage. This method does not consume or release
     * the frame payload.</p>
     */
    public Buffer toBuffer() {
        long payloadLength = header.getPayloadLength();
        if (payloadLength != payload.length()) {
            throw new WebSocketFrameException(
                    "Frame payload length mismatch: header=" + payloadLength + ", actual=" + payload.length());
        }

        Buffer frame = Buffer.alloc(Math.addExact(header.size(), payload.length()));
        try {
            int firstByte = (header.isFin() ? 0x80 : 0) | header.getOpCode().code();
            int maskBit = header.isMasked() ? 0x80 : 0;
            frame.accumulateByte((byte) firstByte);

            if (payloadLength <= 125) {
                frame.accumulateByte((byte) (maskBit | (int) payloadLength));
            } else if (payloadLength <= 0xFFFF) {
                frame.accumulateByte((byte) (maskBit | 126));
                frame.accumulateUnsignedShort((int) payloadLength);
            } else {
                frame.accumulateByte((byte) (maskBit | 127));
                frame.accumulateLong(payloadLength);
            }

            byte[] payloadBytes = payload.getBytes();
            if (header.isMasked()) {
                frame.accumulateInt(header.getMaskingKey());
                payloadBytes = WebSocketFrameHeader.unmask(payloadBytes, header.getMaskingKey());
            }
            frame.accumulateBytes(payloadBytes);
            return frame;
        } catch (Exception e) {
            frame.release();
            throw new WebSocketFrameException("WebSocket frame encoding failed: " + e.getMessage());
        }
    }

    static boolean isControlFrame(OpCode opcode) {
        return opcode == OpCode.CLOSE || opcode == OpCode.PING || opcode == OpCode.PONG;
    }

    static boolean isDataFrame(OpCode opcode) {
        return opcode == OpCode.TEXT || opcode == OpCode.BINARY;
    }

}
