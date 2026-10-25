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
import org.traffichunter.titan.core.channel.InMemoryNetChannel;
import org.traffichunter.titan.core.util.buffer.Buffer;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author yun
 */
class WebSocketFrameEncoderTest {

    @Test
    void release_input_after_encoding_server_frame() {
        Buffer input = Buffer.heap().alloc("OK");

        Buffer encoded = new WebSocketFrameEncoder(WebSocketSide.SERVER)
                .encode(new InMemoryNetChannel(), input);

        assertThat(encoded).isNotNull();
        assertThat(encoded.getBytes()).containsExactly((byte) 0x81, 0x02, 'O', 'K');
        assertThat(input.byteBuf().refCnt()).isZero();

        encoded.release();
    }

    @Test
    void release_input_after_encoding_client_frame() {
        Buffer input = Buffer.heap().alloc("OK");

        Buffer encoded = new WebSocketFrameEncoder(WebSocketSide.CLIENT)
                .encode(new InMemoryNetChannel(), input);

        assertThat(encoded).isNotNull();
        assertThat(encoded.getUnsignedByte(0)).isEqualTo((short) 0x81);
        assertThat(encoded.getUnsignedByte(1) & 0x80).isEqualTo(0x80);
        assertThat(input.byteBuf().refCnt()).isZero();

        encoded.release();
    }

    @Test
    void send_utf8_stomp_frame_as_text() {
        Buffer input = Buffer.heap().alloc("MESSAGE\n\n주문\0".getBytes(StandardCharsets.UTF_8));

        Buffer encoded = new WebSocketFrameEncoder(WebSocketSide.SERVER)
                .encode(new InMemoryNetChannel(), input);

        assertThat(encoded).isNotNull();
        assertThat(encoded.getUnsignedByte(0)).isEqualTo((short) 0x81);

        encoded.release();
    }

    @Test
    void send_stomp_frame_with_non_utf8_body_as_binary() {
        Buffer input = Buffer.heap().alloc(new byte[]{'M', 'E', 'S', 'S', 'A', 'G', 'E', '\n', '\n', (byte) 0xFF, 0x00});

        Buffer encoded = new WebSocketFrameEncoder(WebSocketSide.SERVER)
                .encode(new InMemoryNetChannel(), input);

        assertThat(encoded).isNotNull();
        assertThat(encoded.getUnsignedByte(0)).isEqualTo((short) 0x82);
        assertThat(encoded.getUnsignedByte(11)).isEqualTo((short) 0xFF);

        encoded.release();
    }

    @Test
    void send_client_stomp_frame_with_non_utf8_body_as_binary() {
        Buffer input = Buffer.heap().alloc(new byte[]{'S', 'E', 'N', 'D', '\n', '\n', (byte) 0xFF, 0x00});

        Buffer encoded = new WebSocketFrameEncoder(WebSocketSide.CLIENT)
                .encode(new InMemoryNetChannel(), input);

        assertThat(encoded).isNotNull();
        assertThat(encoded.getUnsignedByte(0)).isEqualTo((short) 0x82);
        assertThat(encoded.getUnsignedByte(1) & 0x80).isEqualTo(0x80);

        encoded.release();
    }
}
