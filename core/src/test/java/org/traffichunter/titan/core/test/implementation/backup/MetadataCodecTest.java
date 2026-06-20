package org.traffichunter.titan.core.test.implementation.backup;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.Test;
import org.traffichunter.titan.core.resilience.backup.DecodedMetadata;
import org.traffichunter.titan.core.resilience.backup.InvalidBackupRecordException;
import org.traffichunter.titan.core.resilience.backup.Metadata;
import org.traffichunter.titan.core.resilience.backup.MetadataCodec;
import org.traffichunter.titan.core.resilience.backup.TruncatedBackupRecordException;
import org.traffichunter.titan.core.resilience.backup.Type;
import org.traffichunter.titan.core.util.buffer.Buffer;

import static org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;

/**
 * @author yun
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class MetadataCodecTest {

    @Test
    void encode_and_decode_round_trip_binary_payload() {
        byte[] payloadBytes = new byte[] { 'a', '\n', 0, (byte) 0xff };
        Buffer payload = Buffer.alloc(payloadBytes);
        Buffer encoded = null;

        try {
            Metadata metadata = Metadata.create(Type.MESSAGE_APPEND, 100, "/queue/orders", payload);

            encoded = MetadataCodec.encode(metadata);
            DecodedMetadata decoded = MetadataCodec.decode(encoded);

            assertThat(decoded.length()).isEqualTo(encoded.length());
            assertThat(decoded.metadata().magic()).isEqualTo(MetadataCodec.MAGIC);
            assertThat(decoded.metadata().version()).isEqualTo(MetadataCodec.VERSION);
            assertThat(decoded.metadata().recordType()).isEqualTo(Type.MESSAGE_APPEND);
            assertThat(decoded.metadata().timestamp()).isEqualTo(100);
            assertThat(decoded.metadata().destinationPath()).isEqualTo("/queue/orders");
            assertThat(decoded.metadata().payload()).containsExactly(payloadBytes);
        } finally {
            payload.release();
            if (encoded != null) {
                encoded.release();
            }
        }
    }

    @Test
    void encode_does_not_release_input_payload() {
        Buffer payload = Buffer.alloc("payload", UTF_8);
        Buffer encoded = null;

        try {
            Metadata metadata = Metadata.create(Type.MESSAGE_APPEND, 100, "/queue/orders", payload);
            encoded = MetadataCodec.encode(metadata);

            assertThat(payload.byteBuf().refCnt()).isEqualTo(1);
        } finally {
            payload.release();
            if (encoded != null) {
                encoded.release();
            }
        }
    }

    @Test
    void invalid_magic_should_fail() {
        assertInvalidRecord(0, (byte) 0);
    }

    @Test
    void unsupported_version_should_fail() {
        assertInvalidRecord(Integer.BYTES, (byte) 2);
    }

    @Test
    void unknown_type_should_fail() {
        assertInvalidRecord(Integer.BYTES + Short.BYTES + 1, (byte) 99);
    }

    @Test
    void truncated_header_should_fail() {
        byte[] truncated = new byte[MetadataCodec.HEADER_SIZE - 1];

        assertThatThrownBy(() -> MetadataCodec.decode(truncated, 0))
                .isInstanceOf(TruncatedBackupRecordException.class);
    }

    @Test
    void truncated_body_should_fail() {
        Buffer encoded = encodeSample();
        try {
            byte[] bytes = encoded.getBytes(0, encoded.length() - 1);

            assertThatThrownBy(() -> MetadataCodec.decode(bytes, 0))
                    .isInstanceOf(TruncatedBackupRecordException.class);
        } finally {
            encoded.release();
        }
    }

    @Test
    void crc32_mismatch_should_fail() {
        Buffer encoded = encodeSample();
        try {
            byte[] bytes = encoded.getBytes();
            bytes[bytes.length - 1] = (byte) (bytes[bytes.length - 1] + 1);

            assertThatThrownBy(() -> MetadataCodec.decode(bytes, 0))
                    .isInstanceOf(InvalidBackupRecordException.class)
                    .hasMessageContaining("crc32");
        } finally {
            encoded.release();
        }
    }

    private void assertInvalidRecord(int index, byte value) {
        Buffer encoded = encodeSample();
        try {
            byte[] bytes = encoded.getBytes();
            bytes[index] = value;

            assertThatThrownBy(() -> MetadataCodec.decode(bytes, 0))
                    .isInstanceOf(InvalidBackupRecordException.class);
        } finally {
            encoded.release();
        }
    }

    private Buffer encodeSample() {
        Buffer payload = Buffer.alloc("payload", UTF_8);
        try {
            return MetadataCodec.encode(Metadata.create(Type.MESSAGE_APPEND, 100, "/queue/orders", payload));
        } finally {
            payload.release();
        }
    }
}
