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
}
