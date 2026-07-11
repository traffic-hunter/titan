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

import org.traffichunter.titan.core.util.Assert;
import org.traffichunter.titan.core.util.buffer.Buffer;

import java.security.SecureRandom;

/**
 * Refer to <a href="https://datatracker.ietf.org/doc/html/rfc6455#section-5.2">RFC 6455</a>
 * <p>
 * WebSocket frame header metadata.
 *</p>
 * <p>
 * Titan does not currently support WebSocket extensions, so RSV1, RSV2, and RSV3 are expected
 * to be {@code 0} when a frame is decoded.
 * </p>
 *
 * <pre>
 *  0                   1                   2                   3
 *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-------+-+-------------+-------------------------------+
 * |F|R|R|R| opcode|M| Payload len | Extended payload length       |
 * |I|S|S|S|  (4)  |A|     (7)     |      (16/64, optional)        |
 * |N|V|V|V|       |S|             |                               |
 * | |1|2|3|       |K|             |                               |
 * +-+-+-+-+-------+-+-------------+-------------------------------+
 * | Masking-key, if MASK set      | Payload data                  |
 * +-------------------------------+-------------------------------+
 *
 * FIN            1 bit   Last fragment of a WebSocket message.
 * RSV1/2/3       1 bit   Reserved for negotiated extensions; unsupported here.
 * opcode         4 bits  Frame type, such as text, binary, close, ping, or pong.
 * MASK           1 bit   Indicates whether payload bytes are masked.
 * Payload len    7 bits  Inline length when 0..125, or 126/127 for extended length.
 * Extended len   16/64   Present only when Payload len is 126 or 127.
 * Masking-key    32 bits Present only when MASK is set.
 * </pre>
 *
 * @author yun
 */
public class WebSocketFrameHeader {

    public static final int MIN_FRAME_HEADER_LENGTH = 2;
    public static final int MAX_FRAME_HEADER_LENGTH = 14;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final byte firstByte;
    private final boolean masked;
    private final long payloadLength;
    private final int maskingKey;

    private WebSocketFrameHeader(Builder builder) {
        this.firstByte = builder.firstByte;
        this.masked = builder.masked;
        this.payloadLength = builder.payloadLength;
        this.maskingKey = builder.maskingKey;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static int generateMaskingKey() {
        return SECURE_RANDOM.nextInt();
    }

    static Buffer unmask(Buffer payload, int maskingKey) {
        try {
            return Buffer.alloc(unmask(payload.getBytes(), maskingKey));
        } finally {
            payload.release();
        }
    }

    static byte[] unmask(byte[] payload, int maskingKey) {
        byte[] masked = new byte[payload.length];
        for (int i = 0; i < payload.length; i++) {
            masked[i] = (byte) (payload[i] ^ maskByte(maskingKey, i));
        }
        return masked;
    }

    Buffer mask(Buffer payload) {
        try {
            return Buffer.alloc(mask(payload.getBytes()));
        } finally {
            payload.release();
        }
    }

    byte[] mask(byte[] payload) {
        byte[] masked = new byte[payload.length];
        for (int i = 0; i < payload.length; i++) {
            masked[i] = (byte) (payload[i] ^ maskByte(maskingKey, i));
        }
        return masked;
    }

    public enum OpCode {
        CONTINUATION(0),
        TEXT(1),
        BINARY(2),
        CLOSE(8),
        PING(9),
        PONG(10),
        ;

        private final int code;

        OpCode(int code) {
            this.code = code;
        }

        public static OpCode of(int code) {
            for (OpCode opCode : values()) {
                if (opCode.code == code) {
                    return opCode;
                }
            }

            throw new WebSocketFrameException("Unknown opcode: " + code);
        }

        public int code() {
            return code;
        }
    }

    public boolean isPayloadEmpty() {
        return payloadLength == 0;
    }

    public long getPayloadLength() {
        return payloadLength;
    }

    public int getMaskingKey() {
        return maskingKey;
    }

    public boolean isFin() {
        return (firstByte & 0x80) != 0;
    }

    public OpCode getOpCode() {
        return OpCode.of(firstByte & 0xF);
    }

    public boolean isMasked() {
        return masked;
    }

    public int size() {
        int size = 2;
        if (payloadLength > 0xFFFF) {
            size += Long.BYTES;
        } else if (payloadLength > 125) {
            size += Short.BYTES;
        }
        if (masked) {
            size += Integer.BYTES;
        }
        return size;
    }

    private static byte maskByte(int maskingKey, int index) {
        int shift = 24 - ((index % 4) * 8);
        return (byte) ((maskingKey >> shift) & 0xFF);
    }

    public static class Builder {

        private byte firstByte;
        private boolean masked;
        private long payloadLength;
        private int maskingKey;

        public Builder op(OpCode opcode, boolean fin) {
            this.firstByte = (byte) (opcode.code() | (fin ? 0x80 : 0));
            return this;
        }

        public Builder masked(int maskingKey) {
            this.masked = true;
            this.maskingKey = maskingKey;
            return this;
        }

        public Builder payloadLength(long payloadLength) {
            Assert.checkArgument(payloadLength >= 0, "payloadLength must be non-negative");
            this.payloadLength = payloadLength;
            return this;
        }

        public WebSocketFrameHeader build() {
            return new WebSocketFrameHeader(this);
        }
    }
}
