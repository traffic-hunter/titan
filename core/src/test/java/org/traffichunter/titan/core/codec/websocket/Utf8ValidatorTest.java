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

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author yun
 */
class Utf8ValidatorTest {

    @Test
    void accept_ascii_korean_and_four_byte_characters() {
        assertThat(isValid("SEND\ndestination:/queue/a\n\n{\"id\":1}\0".getBytes(StandardCharsets.UTF_8))).isTrue();
        assertThat(isValid("주문이 체결되었습니다".getBytes(StandardCharsets.UTF_8))).isTrue();
        assertThat(isValid("😀".getBytes(StandardCharsets.UTF_8))).isTrue();
        assertThat(isValid(new byte[0])).isTrue();
    }

    @Test
    void reject_bytes_that_never_appear_in_utf8() {
        assertThat(isValid(bytes(0xFF))).isFalse();
        assertThat(isValid(bytes(0xC0, 0xAF))).isFalse();
    }

    @Test
    void reject_overlong_surrogate_and_out_of_range_sequences() {
        assertThat(isValid(bytes(0xE0, 0x80, 0xAF))).isFalse();
        assertThat(isValid(bytes(0xED, 0xA0, 0x80))).isFalse();
        assertThat(isValid(bytes(0xF0, 0x80, 0x80, 0xAF))).isFalse();
        assertThat(isValid(bytes(0xF4, 0x90, 0x80, 0x80))).isFalse();
    }

    @Test
    void reject_character_cut_at_the_end() {
        assertThat(isValid(bytes(0xED, 0x95))).isFalse();
    }

    @Test
    void check_bytes_around_the_eight_byte_ascii_skip() {
        assertThat(isValid("12345678한".getBytes(StandardCharsets.UTF_8))).isTrue();
        assertThat(isValid("1234567한".getBytes(StandardCharsets.UTF_8))).isTrue();
        assertThat(isValid(concat("12345678".getBytes(StandardCharsets.US_ASCII), bytes(0xFF)))).isFalse();
        assertThat(isValid(concat("1234567".getBytes(StandardCharsets.US_ASCII), bytes(0xFF), "12345678".getBytes(StandardCharsets.US_ASCII)))).isFalse();
    }

    @Test
    void continue_character_across_updates() {
        Buffer first = Buffer.heap().alloc(bytes(0xED, 0x95));
        Buffer second = Buffer.heap().alloc(bytes(0x9C));
        Utf8Validator validator = new Utf8Validator();

        assertThat(validator.update(first, 0, first.length())).isTrue();
        assertThat(validator.isComplete()).isFalse();
        assertThat(validator.update(second, 0, second.length())).isTrue();
        assertThat(validator.isComplete()).isTrue();

        first.release();
        second.release();
    }

    private static boolean isValid(byte[] bytes) {
        Buffer buffer = Buffer.heap().alloc(bytes);
        try {
            return Utf8Validator.isValid(buffer, 0, buffer.length());
        } finally {
            buffer.release();
        }
    }

    private static byte[] bytes(int... values) {
        byte[] bytes = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            bytes[i] = (byte) values[i];
        }
        return bytes;
    }

    private static byte[] concat(byte[]... parts) {
        int length = 0;
        for (byte[] part : parts) {
            length += part.length;
        }
        byte[] joined = new byte[length];
        int position = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, joined, position, part.length);
            position += part.length;
        }
        return joined;
    }
}
