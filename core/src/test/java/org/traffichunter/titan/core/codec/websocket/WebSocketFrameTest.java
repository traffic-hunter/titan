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

import org.junit.jupiter.api.Test;
import org.traffichunter.titan.core.util.buffer.Buffer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.traffichunter.titan.core.codec.websocket.WebSocketFrameHeader.OpCode.TEXT;

/**
 * @author yun
 */
class WebSocketFrameTest {

    @Test
    void convert_unmasked_frame_to_buffer() {
        Buffer payload = Buffer.heap().alloc("OK");
        WebSocketFrameHeader header = WebSocketFrameHeader.builder()
                .op(TEXT, true)
                .payloadLength(payload.length())
                .build();
        WebSocketFrame frame = new WebSocketFrame(header, payload);

        Buffer encoded = frame.encode();

        assertThat(encoded.getBytes()).containsExactly((byte) 0x81, 0x02, 'O', 'K');
        assertThat(payload.toString()).isEqualTo("OK");

        encoded.release();
        payload.release();
    }

    @Test
    void convert_masked_frame_to_buffer() {
        Buffer payload = Buffer.heap().alloc("OK");
        WebSocketFrameHeader header = WebSocketFrameHeader.builder()
                .op(TEXT, true)
                .masked(0x01020304)
                .payloadLength(payload.length())
                .build();
        WebSocketFrame frame = new WebSocketFrame(header, payload);

        Buffer encoded = frame.encode();

        assertThat(encoded.getBytes()).containsExactly(
                (byte) 0x81,
                (byte) 0x82,
                0x01,
                0x02,
                0x03,
                0x04,
                (byte) ('O' ^ 0x01),
                (byte) ('K' ^ 0x02)
        );
        assertThat(payload.toString()).isEqualTo("OK");

        encoded.release();
        payload.release();
    }

    @Test
    void reject_payload_length_mismatch() {
        Buffer payload = Buffer.heap().alloc("OK");
        WebSocketFrameHeader header = WebSocketFrameHeader.builder()
                .op(TEXT, true)
                .payloadLength(1)
                .build();
        WebSocketFrame frame = new WebSocketFrame(header, payload);

        assertThatThrownBy(frame::encode)
                .isInstanceOf(WebSocketFrameException.class)
                .hasMessageContaining("payload length mismatch");

        payload.release();
    }
}
