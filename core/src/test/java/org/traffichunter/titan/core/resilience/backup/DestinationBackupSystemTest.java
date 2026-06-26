package org.traffichunter.titan.core.resilience.backup;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.traffichunter.titan.core.util.buffer.Buffer;
import org.traffichunter.titan.core.util.file.FileHandler;

import static org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;

/**
 * @author yun
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class DestinationBackupSystemTest {

    @TempDir
    private Path tempDir;

    @Test
    void record_writes_destination_specific_file() throws Exception {
        Metadata orders = metadata("/queue/orders", "orders");
        Metadata payments = metadata("/queue/payments", "payments");

        try (DestinationBackupSystem system = new DestinationBackupSystem(tempDir, BackupOption.fromConfig("aof", "no", null))) {
            system.record(orders);
            system.record(payments);
        }

        assertThat(FileHandler.exists(FileHandler.resolveDestinationFile(tempDir, "/queue/orders"))).isTrue();
        assertThat(FileHandler.exists(FileHandler.resolveDestinationFile(tempDir, "/queue/payments"))).isTrue();
    }

    @Test
    void restore_replays_one_destination_file() throws Exception {
        Metadata first = metadata("/queue/orders", "first");
        Metadata second = metadata("/queue/orders", "second");
        List<String> restored = new ArrayList<>();

        try (DestinationBackupSystem system = new DestinationBackupSystem(tempDir, BackupOption.fromConfig("aof", "no", null))) {
            system.record(first);
            system.record(second);

            assertThat(system.inspect("/queue/orders")).isTrue();
            assertThat(system.restore("/queue/orders", metadata -> restored.add(new String(metadata.payload(), UTF_8))))
                    .isTrue();
        }

        assertThat(restored).containsExactly("first", "second");
    }

    @Test
    void record_fails_after_close() throws Exception {
        DestinationBackupSystem system = new DestinationBackupSystem(tempDir, BackupOption.fromConfig("aof", "no", null));
        system.record(metadata("/queue/orders", "payload"));
        system.close();

        assertThatThrownBy(() -> system.record(metadata("/queue/orders", "next")))
                .isInstanceOf(BackupException.class)
                .hasMessageContaining("stopped");
    }

    @Test
    void unsupported_backup_types_fail_fast() {
        assertThatThrownBy(() -> new DestinationBackupSystem(tempDir, BackupOption.fromConfig("rdb", "no", null)))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Only aof backup is supported");

        assertThatThrownBy(() -> new DestinationBackupSystem(tempDir, BackupOption.fromConfig("all", "no", null)))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Only aof backup is supported");
    }

    private Metadata metadata(String destination, String payload) {
        Buffer buffer = Buffer.alloc(payload, UTF_8);
        try {
            return Metadata.create(Type.MESSAGE_APPEND, 100, destination, buffer);
        } finally {
            buffer.release();
        }
    }
}
