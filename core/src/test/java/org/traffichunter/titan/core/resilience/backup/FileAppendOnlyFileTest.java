package org.traffichunter.titan.core.resilience.backup;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.channels.FileLock;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.LongSupplier;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.Test;
import org.traffichunter.titan.core.util.buffer.Buffer;
import org.traffichunter.titan.core.util.file.FileHandle;

import static org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;

/**
 * @author yun
 */
@NullMarked
@DisplayNameGeneration(ReplaceUnderscores.class)
class FileAppendOnlyFileTest {

    @Test
    void backup_option_can_be_created_from_config_values() {
        BackupOption option = BackupOption.fromConfig("aof", "no", "fail-on-truncated-tail");

        assertThat(option.type()).isEqualTo(BackupType.AOF);
        assertThat(option.syncPolicy()).isEqualTo(AofSyncPolicy.NO);
        assertThat(option.recoveryPolicy()).isEqualTo(AofRecoveryPolicy.FAIL_ON_TRUNCATED_TAIL);
    }

    @Test
    void append_and_replay_records_in_order() {
        FakeFileHandle fileHandle = new FakeFileHandle();
        MutableClock clock = new MutableClock();

        try (AppendOnlyFile file = new FileAppendOnlyFile(
                fileHandle,
                BackupOption.fromConfig("no", null),
                clock
        )) {
            Metadata first = metadata(Type.CREATE_QUEUE, "/queue/a", "first");
            Metadata second = metadata(Type.MESSAGE_APPEND, "/queue/a", "second");

            long firstOffset = file.append(first);
            long secondOffset = file.append(second);

            List<Metadata> replayed = new ArrayList<>();
            file.replay(replayed::add);

            assertThat(firstOffset).isZero();
            assertThat(secondOffset).isPositive();
            assertThat(replayed).hasSize(2);
            assertThat(replayed.get(0).recordType()).isEqualTo(Type.CREATE_QUEUE);
            assertThat(replayed.get(1).recordType()).isEqualTo(Type.MESSAGE_APPEND);
            assertThat(new String(replayed.get(1).payload(), UTF_8)).isEqualTo("second");
        }
    }

    @Test
    void every_fsync_policy_forces_each_append() {
        FakeFileHandle fileHandle = new FakeFileHandle();

        try (AppendOnlyFile file = new FileAppendOnlyFile(
                fileHandle,
                BackupOption.fromConfig("every", null),
                new MutableClock()
        )) {
            file.append(metadata(Type.MESSAGE_APPEND, "/queue/a", "one"));
            file.append(metadata(Type.MESSAGE_APPEND, "/queue/a", "two"));

            assertThat(fileHandle.forceCount()).isEqualTo(2);
        }
    }

    @Test
    void every_sec_fsync_policy_forces_after_interval() {
        FakeFileHandle fileHandle = new FakeFileHandle();
        MutableClock clock = new MutableClock();

        try (AppendOnlyFile file = new FileAppendOnlyFile(
                fileHandle,
                BackupOption.defaults(),
                clock
        )) {
            file.append(metadata(Type.MESSAGE_APPEND, "/queue/a", "one"));
            clock.advanceMillis(999);
            file.append(metadata(Type.MESSAGE_APPEND, "/queue/a", "two"));
            clock.advanceMillis(1);
            file.append(metadata(Type.MESSAGE_APPEND, "/queue/a", "three"));

            assertThat(fileHandle.forceCount()).isEqualTo(1);
        }
    }

    @Test
    void no_sync_policy_does_not_force_on_append_but_explicit_fsync_does() {
        FakeFileHandle fileHandle = new FakeFileHandle();

        try (AppendOnlyFile file = new FileAppendOnlyFile(
                fileHandle,
                BackupOption.fromConfig("no", null),
                new MutableClock()
        )) {
            file.append(metadata(Type.MESSAGE_APPEND, "/queue/a", "one"));
            assertThat(fileHandle.forceCount()).isZero();

            file.fsync();
            assertThat(fileHandle.forceCount()).isEqualTo(1);
        }
    }

    @Test
    void truncated_tail_can_be_ignored() {
        FakeFileHandle fileHandle = new FakeFileHandle();

        try (AppendOnlyFile file = new FileAppendOnlyFile(
                fileHandle,
                BackupOption.fromConfig("no", null),
                new MutableClock()
        )) {
            file.append(metadata(Type.MESSAGE_APPEND, "/queue/a", "one"));
            file.append(metadata(Type.MESSAGE_APPEND, "/queue/a", "two"));
            fileHandle.truncateBytes(fileHandle.length() - 1);

            List<Metadata> replayed = new ArrayList<>();
            file.replay(replayed::add);

            assertThat(replayed).hasSize(1);
            assertThat(new String(replayed.getFirst().payload(), UTF_8)).isEqualTo("one");
        }
    }

    @Test
    void truncated_tail_can_fail_replay() {
        FakeFileHandle fileHandle = new FakeFileHandle();

        try (AppendOnlyFile file = new FileAppendOnlyFile(
                fileHandle,
                BackupOption.fromConfig("no", "fail_on_truncated_tail"),
                new MutableClock()
        )) {
            file.append(metadata(Type.MESSAGE_APPEND, "/queue/a", "one"));
            fileHandle.truncateBytes(fileHandle.length() - 1);

            assertThatThrownBy(() -> file.replay(metadata -> { }))
                    .isInstanceOf(TruncatedBackupRecordException.class);
        }
    }

    @Test
    void crc_mismatch_fails_replay() {
        FakeFileHandle fileHandle = new FakeFileHandle();

        try (AppendOnlyFile file = new FileAppendOnlyFile(
                fileHandle,
                BackupOption.fromConfig("no", null),
                new MutableClock()
        )) {
            file.append(metadata(Type.MESSAGE_APPEND, "/queue/a", "one"));
            fileHandle.flipLastByte();

            assertThatThrownBy(() -> file.replay(metadata -> { }))
                    .isInstanceOf(InvalidBackupRecordException.class);
        }
    }

    private Metadata metadata(Type type, String destination, String payload) {
        Buffer buffer = Buffer.alloc(payload, UTF_8);
        try {
            return Metadata.create(type, 100, destination, buffer);
        } finally {
            buffer.release();
        }
    }

    private static final class MutableClock implements LongSupplier {

        private long now;

        @Override
        public long getAsLong() {
            return now;
        }

        void advanceMillis(long millis) {
            now += millis;
        }
    }

    private static final class FakeFileHandle implements FileHandle {

        private byte[] bytes = new byte[0];
        private int forceCount;

        @Override
        public Path path() {
            return Path.of("fake.aof");
        }

        @Override
        public Buffer readAll() {
            return Buffer.alloc(bytes);
        }

        @Override
        public Buffer read(long position, int length) {
            byte[] slice = Arrays.copyOfRange(bytes, (int) position, (int) position + length);
            return Buffer.alloc(slice);
        }

        @Override
        public Instant lastModified() {
            return Instant.EPOCH;
        }

        @Override
        public long size() {
            return bytes.length;
        }

        @Override
        public void write(Buffer source) {
            bytes = source.getBytes();
        }

        @Override
        public void write(long position, Buffer source) {
            byte[] sourceBytes = source.getBytes();
            byte[] next = Arrays.copyOf(bytes, Math.max(bytes.length, (int) position + sourceBytes.length));
            System.arraycopy(sourceBytes, 0, next, (int) position, sourceBytes.length);
            bytes = next;
        }

        @Override
        public long append(Buffer source) {
            long offset = bytes.length;
            byte[] sourceBytes = source.getBytes();
            byte[] next = Arrays.copyOf(bytes, bytes.length + sourceBytes.length);
            System.arraycopy(sourceBytes, 0, next, bytes.length, sourceBytes.length);
            bytes = next;
            return offset;
        }

        @Override
        public void truncate(long size) {
            bytes = Arrays.copyOf(bytes, (int) size);
        }

        @Override
        public void force(boolean metadata) {
            forceCount++;
        }

        @Override
        public FileLock lock() {
            throw new UnsupportedOperationException();
        }

        @Override
        public @Nullable FileLock tryLock() {
            return null;
        }

        @Override
        public void close() {
        }

        int forceCount() {
            return forceCount;
        }

        int length() {
            return bytes.length;
        }

        void truncateBytes(int length) {
            bytes = Arrays.copyOf(bytes, length);
        }

        void flipLastByte() {
            bytes[bytes.length - 1] = (byte) (bytes[bytes.length - 1] + 1);
        }
    }
}
