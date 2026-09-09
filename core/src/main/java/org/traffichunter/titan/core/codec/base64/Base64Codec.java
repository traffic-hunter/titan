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
package org.traffichunter.titan.core.codec.base64;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Utility methods for Base64 encoding and decoding.
 *
 * <p>Encoded output is generated without padding.</p>
 *
 * @author yungwang-o
 */
public final class Base64Codec {

    private static final Base64.Encoder BASE64_ENCODER = Base64.getEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_DECODER = Base64.getDecoder();

    /**
     * Encodes raw bytes as Base64.
     */
    public static byte[] encode(final byte[] src) {
        return BASE64_ENCODER.encode(src);
    }

    /**
     * Encodes a UTF-8 string as Base64.
     */
    public static byte[] encode(final String src) {
        return encode(src, StandardCharsets.UTF_8);
    }

    /**
     * Encodes a string with the given charset as Base64.
     */
    public static byte[] encode(final String src, final Charset charset) {
        return BASE64_ENCODER.encode(src.getBytes(charset));
    }

    /**
     * Encodes the remaining bytes of a {@link ByteBuffer} as Base64.
     */
    public static byte[] encode(final ByteBuffer src) {
        return BASE64_ENCODER.encode(src).array();
    }

    public static String encodeToStringUtf8(final byte[] src) {
        return new String(encode(src), StandardCharsets.UTF_8);
    }

    public static String decodeToStringUtf8(final byte[] src) {
        return new String(decode(src), StandardCharsets.UTF_8);
    }

    /**
     * Decodes Base64 bytes.
     */
    public static byte[] decode(final byte[] src) {
        return BASE64_DECODER.decode(src);
    }

    /**
     * Decodes a Base64 string.
     */
    public static byte[] decode(final String src) {
        return BASE64_DECODER.decode(src);
    }

    /**
     * Decodes the remaining bytes of a Base64 {@link ByteBuffer}.
     */
    public static byte[] decode(final ByteBuffer src) {
        return BASE64_DECODER.decode(src).array();
    }

    private Base64Codec() { }
}
