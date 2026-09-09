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
package org.traffichunter.titan.core.codec.websocket;

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
    public Buffer encode() {
        long payloadLength = header.getPayloadLength();
        if (payloadLength != payload.length()) {
            throw new WebSocketFrameException("Frame payload length mismatch: header=" + payloadLength + ", actual=" + payload.length());
        }

        Buffer frame = Buffer.heap().alloc(Math.addExact(header.size(), payload.length()));
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

    public static boolean isControlFrame(OpCode opcode) {
        return opcode == OpCode.CLOSE || opcode == OpCode.PING || opcode == OpCode.PONG;
    }

    public static boolean isDataFrame(OpCode opcode) {
        return opcode == OpCode.TEXT || opcode == OpCode.BINARY;
    }

    public boolean isControlFrame() {
        return isControlFrame(header.getOpCode());
    }

    public boolean isDataFrame() {
        return isDataFrame(header.getOpCode());
    }
}
