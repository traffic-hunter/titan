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

import org.traffichunter.titan.core.util.buffer.Buffer;

/**
 * Validates UTF-8 incrementally, so a character may span several calls to {@link #update}.
 *
 * @author yun
 */
final class Utf8Validator {

    private static final long NON_ASCII_BITS = 0x8080808080808080L;

    // Continuation bytes still needed by the current character, and the range the next one must
    // fall in. The range narrows after E0, ED, F0 and F4 to reject overlong forms, surrogates and
    // code points above U+10FFFF.
    private int needed;
    private int lower = 0x80;
    private int upper = 0xBF;

    static boolean isValid(Buffer buffer, int index, int length) {
        Utf8Validator validator = new Utf8Validator();
        return validator.update(buffer, index, length) && validator.isComplete();
    }

    /**
     * Returns {@code false} at the first byte that cannot continue valid UTF-8.
     */
    boolean update(Buffer buffer, int index, int length) {
        int i = index;
        int end = index + length;
        while (i < end) {
            // Skip eight ASCII bytes at once, the common case for JSON bodies and STOMP headers.
            if (needed == 0 && i + Long.BYTES <= end && (buffer.getLong(i) & NON_ASCII_BITS) == 0) {
                i += Long.BYTES;
                continue;
            }

            int b = buffer.getByte(i++) & 0xFF;
            if (needed == 0) {
                if (b <= 0x7F) {
                    continue;
                } else if (b >= 0xC2 && b <= 0xDF) {
                    needed = 1;
                } else if (b == 0xE0) {
                    needed = 2;
                    lower = 0xA0;
                } else if (b == 0xED) {
                    needed = 2;
                    upper = 0x9F;
                } else if (b >= 0xE1 && b <= 0xEF) {
                    needed = 2;
                } else if (b == 0xF0) {
                    needed = 3;
                    lower = 0x90;
                } else if (b == 0xF4) {
                    needed = 3;
                    upper = 0x8F;
                } else if (b >= 0xF1 && b <= 0xF3) {
                    needed = 3;
                } else {
                    return false;
                }
                continue;
            }

            if (b < lower || b > upper) {
                return false;
            }
            lower = 0x80;
            upper = 0xBF;
            needed--;
        }
        return true;
    }

    /**
     * Returns whether the bytes so far end on a character boundary.
     */
    boolean isComplete() {
        return needed == 0;
    }
}
