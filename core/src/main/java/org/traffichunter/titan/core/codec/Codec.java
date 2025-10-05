/*
 * The MIT License
 *
 * Copyright (c) 2025 traffic-hunter
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.traffichunter.titan.core.codec;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * @author yungwang-o
 */
public final class Codec {

    private static final Base64.Encoder BASE64_ENCODER = Base64.getEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_DECODER = Base64.getDecoder();

    public static byte[] encode(final byte[] src) {
        return BASE64_ENCODER.encode(src);
    }

    public static byte[] encode(final String src) {
        return encode(src, StandardCharsets.UTF_8);
    }

    public static byte[] encode(final String src, final Charset charset) {
        return BASE64_ENCODER.encode(src.getBytes(charset));
    }

    public static byte[] encode(final ByteBuffer src) {
        return BASE64_ENCODER.encode(src).array();
    }

    public static byte[] decode(final byte[] src) {
        return BASE64_DECODER.decode(src);
    }

    public static byte[] decode(final String src) {
        return BASE64_DECODER.decode(src);
    }

    public static byte[] decode(final ByteBuffer src) {
        return BASE64_DECODER.decode(src).array();
    }

    private Codec() { }
}
