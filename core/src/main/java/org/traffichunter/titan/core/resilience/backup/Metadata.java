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
package org.traffichunter.titan.core.resilience.backup;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.Arrays;
import org.traffichunter.titan.core.util.buffer.Buffer;

/**
 * Binary AOF record metadata and payload.
 *
 * <p>The record keeps byte arrays defensively copied so that callers cannot
 *  invalidate length and checksum metadata after construction. The source {@link Buffer} passed to
 * {@link #create(Type, long, String, Buffer)} remains owned by the caller.</p>
 *
 * @author yun
 */
public record Metadata(
        int magic,
        short version,
        short type,
        long timestamp,
        int destinationLength,
        int payloadLength,
        int crc32,
        byte[] destination,
        byte[] payload
) {

    /**
     * Defensively copies mutable byte arrays on construction.
     */
    public Metadata {
        destination = destination.clone();
        payload = payload.clone();
    }

    /**
     * Creates metadata from a Titan buffer without releasing or retaining the source buffer.
     */
    public static Metadata create(Type type, long timestamp, String destination, Buffer payload) {
        return create(type, timestamp, destination, payload.getBytes());
    }

    /**
     * Creates metadata from raw bytes using UTF-8 for the destination path.
     */
    public static Metadata create(Type type, long timestamp, String destination, byte[] payload) {
        byte[] destinationBytes = destination.getBytes(UTF_8);
        return new Metadata(
                MetadataCodec.MAGIC,
                MetadataCodec.VERSION,
                (short) type.getValue(),
                timestamp,
                destinationBytes.length,
                payload.length,
                0,
                destinationBytes,
                payload
        );
    }

    /**
     * Returns the typed AOF record operation.
     */
    public Type recordType() {
        return Type.fromValue(type);
    }

    /**
     * Returns the UTF-8 decoded destination path.
     */
    public String destinationPath() {
        return new String(destination, UTF_8);
    }

    @Override
    public byte[] destination() {
        return destination.clone();
    }

    @Override
    public byte[] payload() {
        return payload.clone();
    }

    @Override
    public String toString() {
        return magic + ", " +
                version + ", " +
                type + ", " +
                timestamp + ", " +
                destinationLength + ", " +
                payloadLength + ", " +
                crc32 + ", " +
                Arrays.toString(destination) + ", " +
                Arrays.toString(payload);
    }
}
