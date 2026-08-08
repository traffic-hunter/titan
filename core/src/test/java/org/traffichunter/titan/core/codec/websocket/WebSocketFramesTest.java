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
