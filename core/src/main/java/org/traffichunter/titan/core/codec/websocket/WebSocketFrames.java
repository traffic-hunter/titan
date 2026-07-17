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

import org.traffichunter.titan.core.codec.websocket.WebSocketFrameHeader.OpCode;
import org.traffichunter.titan.core.util.Assert;
import org.traffichunter.titan.core.util.Protocol;
import org.traffichunter.titan.core.util.buffer.Buffer;

import java.nio.charset.StandardCharsets;

/**
 * @author yun
 */
public final class WebSocketFrames {

    public static WebSocketFrame ping(
            Buffer payload,
            WebSocketSide side,
            Protocol protocol
    ) {
        validateControlPayload(payload);
        WebSocketFrameHeader.Builder header = WebSocketFrameHeader.builder()
                .op(WebSocketFrameHeader.OpCode.PING, true)
                .payloadLength(payload.length());

        if (side == WebSocketSide.CLIENT) {
            header.masked(WebSocketFrameHeader.generateMaskingKey());
        }

        return new WebSocketFrame(
                header.build(),
                payload,
                protocol
        );
    }

    public static WebSocketFrame pong(
            Buffer pingPayload,
            WebSocketSide side,
            Protocol protocol
    ) {
        validateControlPayload(pingPayload);
        WebSocketFrameHeader.Builder header = WebSocketFrameHeader.builder()
                .op(WebSocketFrameHeader.OpCode.PONG, true)
                .payloadLength(pingPayload.length());

        if (side == WebSocketSide.CLIENT) {
            header.masked(WebSocketFrameHeader.generateMaskingKey());
        }

        return new WebSocketFrame(
                header.build(),
                pingPayload,
                protocol
        );
    }

    public static WebSocketFrame close(
            int statusCode,
            String reason,
            WebSocketSide side,
            Protocol protocol
    ) {
        byte[] reasonBytes = reason.getBytes(StandardCharsets.UTF_8);

        Assert.check(reasonBytes.length <= 123, () -> new WebSocketFrameException("Close reason must be at most 123 bytes"));

        Buffer payload = Buffer.alloc(Short.BYTES + reasonBytes.length)
                .accumulateUnsignedShort(statusCode)
                .accumulateBytes(reasonBytes);

        WebSocketFrameHeader.Builder header = WebSocketFrameHeader.builder()
                .op(OpCode.CLOSE, true)
                .payloadLength(payload.length());

        if (side == WebSocketSide.CLIENT) {
            header.masked(WebSocketFrameHeader.generateMaskingKey());
        }

        return new WebSocketFrame(
                header.build(),
                payload,
                protocol
        );
    }

    public static WebSocketFrame close(
            Buffer payload,
            WebSocketSide side,
            Protocol protocol
    ) {
        validateControlPayload(payload);
        if (payload.length() == 1) {
            throw new WebSocketFrameException("Close frame payload must be empty or at least 2 bytes");
        }

        WebSocketFrameHeader.Builder header = WebSocketFrameHeader.builder()
                .op(OpCode.CLOSE, true)
                .payloadLength(payload.length());
        if (side == WebSocketSide.CLIENT) {
            header.masked(WebSocketFrameHeader.generateMaskingKey());
        }
        return new WebSocketFrame(header.build(), payload, protocol);
    }

    private static void validateControlPayload(Buffer payload) {
        if (payload.length() > 125) {
            throw new WebSocketFrameException("Control frame payload must be at most 125 bytes");
        }
    }

    private WebSocketFrames() {
    }
}
