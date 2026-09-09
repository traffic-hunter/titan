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
package org.traffichunter.titan.incubator.resilience.backup;

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
        int    magic,
        short  version,
        short  type,
        long   timestamp,
        int    destinationLength,
        int    payloadLength,
        int    crc32,
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
