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
import org.traffichunter.titan.core.channel.InMemoryNetChannel;
import org.traffichunter.titan.core.util.buffer.Buffer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author yun
 */
class WebSocketFrameEncoderTest {

    @Test
    void release_input_after_encoding_server_frame() {
        Buffer input = Buffer.alloc("OK");

        Buffer encoded = new WebSocketFrameEncoder(WebSocketSide.SERVER)
                .encode(new InMemoryNetChannel(), input);

        assertThat(encoded).isNotNull();
        assertThat(encoded.getBytes()).containsExactly((byte) 0x81, 0x02, 'O', 'K');
        assertThat(input.byteBuf().refCnt()).isZero();

        encoded.release();
    }

    @Test
    void release_input_after_encoding_client_frame() {
        Buffer input = Buffer.alloc("OK");

        Buffer encoded = new WebSocketFrameEncoder(WebSocketSide.CLIENT)
                .encode(new InMemoryNetChannel(), input);

        assertThat(encoded).isNotNull();
        assertThat(encoded.getUnsignedByte(0)).isEqualTo((short) 0x81);
        assertThat(encoded.getUnsignedByte(1) & 0x80).isEqualTo(0x80);
        assertThat(input.byteBuf().refCnt()).isZero();

        encoded.release();
    }
}
