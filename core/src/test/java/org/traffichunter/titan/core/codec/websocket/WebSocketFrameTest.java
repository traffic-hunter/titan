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
        Buffer payload = Buffer.alloc("OK");
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
        Buffer payload = Buffer.alloc("OK");
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
        Buffer payload = Buffer.alloc("OK");
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
