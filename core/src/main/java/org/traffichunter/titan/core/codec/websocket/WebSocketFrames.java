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

        Buffer payload = Buffer.heap().alloc(Short.BYTES + reasonBytes.length)
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
