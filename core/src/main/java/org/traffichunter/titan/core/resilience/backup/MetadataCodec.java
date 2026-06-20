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

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.zip.CRC32;
import org.traffichunter.titan.core.util.buffer.Buffer;

/**
 * Codec for Titan AOF metadata records.
 *
 * <p>The v1 layout is big-endian and intentionally self-delimiting:</p>
 *
 * <pre>
 * int    magic
 * short  version
 * short  type
 * long   timestamp
 * int    destinationLength
 * int    payloadLength
 * int    crc32
 * byte[] destination
 * byte[] payload
 * </pre>
 *
 * <p>The CRC32 covers version, type, timestamp, lengths, destination, and payload. Magic and crc32
 * are verified separately and are not included in the checksum input.</p>
 *
 * @author yun
 */
public final class MetadataCodec {

    /**
     * ASCII {@code TAOF}; used as the sync marker for Titan append-only records.
     */
    public static final int MAGIC = 0x54414F46;

    /**
     * Binary metadata record format version.
     */
    public static final short VERSION = 1;

    /**
     * Fixed-size header length before destination and payload bytes.
     */
    public static final int HEADER_SIZE;

    static {
        HEADER_SIZE = combineSize(
                Integer.BYTES,
                Short.BYTES,
                Short.BYTES,
                Long.BYTES,
                Integer.BYTES,
                Integer.BYTES,
                Integer.BYTES
        );
    }

    /**
     * Encodes metadata into a newly allocated Titan buffer.
     *
     * <p>The returned buffer is owned by the caller and must be released by the caller.</p>
     */
    public static Buffer encode(Metadata metadata) {
        byte[] destination = metadata.destination();
        byte[] payload = metadata.payload();
        int crc32 = crc32(
                metadata.version(),
                metadata.type(),
                metadata.timestamp(),
                destination.length,
                payload.length,
                destination,
                payload
        );

        ByteBuffer buffer = ByteBuffer.allocate(HEADER_SIZE + destination.length + payload.length);
        buffer.putInt(MAGIC);
        buffer.putShort(VERSION);
        buffer.putShort(metadata.type());
        buffer.putLong(metadata.timestamp());
        buffer.putInt(destination.length);
        buffer.putInt(payload.length);
        buffer.putInt(crc32);
        buffer.put(destination);
        buffer.put(payload);
        return Buffer.alloc(buffer.array());
    }

    /**
     * Decodes one complete record from a buffer starting at offset zero.
     */
    public static DecodedMetadata decode(Buffer buffer) {
        return decode(buffer.getBytes(), 0);
    }

    /**
     * Decodes one record from {@code bytes} at {@code offset}.
     *
     * <p>The method returns the decoded record and the consumed byte count so replay can move to
     * the next record without searching for magic bytes.</p>
     */
    public static DecodedMetadata decode(byte[] bytes, int offset) {
        if (bytes.length - offset < HEADER_SIZE) {
            throw new TruncatedBackupRecordException("Truncated backup record header", offset);
        }

        ByteBuffer buffer = ByteBuffer.wrap(bytes, offset, bytes.length - offset);
        int magic = buffer.getInt();
        short version = buffer.getShort();
        short type = buffer.getShort();
        long timestamp = buffer.getLong();
        int destinationLength = buffer.getInt();
        int payloadLength = buffer.getInt();
        int crc32 = buffer.getInt();

        if (magic != MAGIC) {
            throw new InvalidBackupRecordException("Invalid backup record magic: " + magic);
        }
        if (version != VERSION) {
            throw new InvalidBackupRecordException("Unsupported backup record version: " + version);
        }
        Type.fromValue(type);
        if (destinationLength < 0) {
            throw new InvalidBackupRecordException("Invalid destination length: " + destinationLength);
        }
        if (payloadLength < 0) {
            throw new InvalidBackupRecordException("Invalid payload length: " + payloadLength);
        }

        int recordLength = HEADER_SIZE + destinationLength + payloadLength;
        if (bytes.length - offset < recordLength) {
            throw new TruncatedBackupRecordException("Truncated backup record body", offset);
        }

        byte[] destination = new byte[destinationLength];
        byte[] payload = new byte[payloadLength];
        buffer.get(destination);
        buffer.get(payload);

        int computedCrc32 = crc32(version, type, timestamp, destinationLength, payloadLength, destination, payload);
        if (crc32 != computedCrc32) {
            throw new InvalidBackupRecordException("Backup record crc32 mismatch");
        }

        return new DecodedMetadata(
                new Metadata(
                        magic,
                        version,
                        type,
                        timestamp,
                        destinationLength,
                        payloadLength,
                        crc32,
                        destination,
                        payload
                ),
                recordLength
        );
    }

    private static int crc32(
            short version,
            short type,
            long timestamp,
            int destinationLength,
            int payloadLength,
            byte[] destination,
            byte[] payload
    ) {
        // Keep the checksum input identical to the documented v1 format: magic and the crc field
        // are excluded, while all replay-relevant metadata and payload bytes are included.
        ByteBuffer header = ByteBuffer.allocate(
                combineSize(Short.BYTES, Short.BYTES, Long.BYTES, Integer.BYTES, Integer.BYTES)
        );
        header.putShort(version);
        header.putShort(type);
        header.putLong(timestamp);
        header.putInt(destinationLength);
        header.putInt(payloadLength);

        CRC32 crc32 = new CRC32();
        crc32.update(header.array());
        crc32.update(destination);
        crc32.update(payload);
        return (int) crc32.getValue();
    }

    private static int combineSize(int... numberByte) {
        return Arrays.stream(numberByte).sum();
    }

    /**
     * Utility class.
     */
    private MetadataCodec() { }
}
