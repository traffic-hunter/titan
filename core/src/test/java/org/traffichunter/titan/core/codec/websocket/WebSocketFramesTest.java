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
import org.traffichunter.titan.core.util.Protocol;
import org.traffichunter.titan.core.util.buffer.Buffer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author yun
 */
class WebSocketFramesTest {

    @Test
    void create_unmasked_server_ping_frame() {
        Buffer payload = Buffer.heap().alloc("OK");
        WebSocketFrame frame = WebSocketFrames.ping(payload, WebSocketSide.SERVER, Protocol.STOMP);

        assertThat(frame.header().getOpCode()).isEqualTo(WebSocketFrameHeader.OpCode.PING);
        assertThat(frame.header().isFin()).isTrue();
        assertThat(frame.header().isMasked()).isFalse();

        payload.release();
    }

    @Test
    void create_masked_client_pong_frame() {
        Buffer payload = Buffer.heap().alloc("OK");
        WebSocketFrame frame = WebSocketFrames.pong(payload, WebSocketSide.CLIENT, Protocol.STOMP);

        assertThat(frame.header().getOpCode()).isEqualTo(WebSocketFrameHeader.OpCode.PONG);
        assertThat(frame.header().isMasked()).isTrue();

        payload.release();
    }

    @Test
    void encode_close_status_and_reason() {
        WebSocketFrame frame = WebSocketFrames.close(1000, "bye", WebSocketSide.SERVER, Protocol.STOMP);

        assertThat(frame.payload().getUnsignedShort(0)).isEqualTo(1000);
        assertThat(frame.payload().getBytes()).containsExactly(0x03, (byte) 0xE8, 'b', 'y', 'e');

        frame.payload().release();
    }

    @Test
    void reject_oversized_control_frame_payload() {
        Buffer payload = Buffer.heap().alloc(new byte[126]);

        assertThatThrownBy(() -> WebSocketFrames.ping(payload, WebSocketSide.SERVER, Protocol.STOMP))
                .isInstanceOf(WebSocketFrameException.class)
                .hasMessageContaining("at most 125 bytes");

        payload.release();
    }

    @Test
    void reject_one_byte_close_payload() {
        Buffer payload = Buffer.heap().alloc(new byte[1]);

        assertThatThrownBy(() -> WebSocketFrames.close(payload, WebSocketSide.SERVER, Protocol.STOMP))
                .isInstanceOf(WebSocketFrameException.class)
                .hasMessageContaining("empty or at least 2 bytes");

        payload.release();
    }
}
