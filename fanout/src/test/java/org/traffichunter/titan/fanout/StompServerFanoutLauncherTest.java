package org.traffichunter.titan.fanout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.traffichunter.titan.bootstrap.Settings;

class StompServerFanoutLauncherTest {

    @Test
    void backup_path_is_resolved_as_directory() {
        Settings.BackupSettings backupSettings = Settings.BackupSettings.fromConfig(
                true,
                "aof",
                "./data/backup",
                "no",
                null
        );

        assertThat(StompServerFanoutLauncher.resolveBackupDirectory(backupSettings))
                .isEqualTo(Path.of("./data/backup"));
    }

    @Test
    void backup_path_rejects_append_only_file_name() {
        Settings.BackupSettings backupSettings = Settings.BackupSettings.fromConfig(
                true,
                "aof",
                "./data/titan.aof",
                "no",
                null
        );

        assertThatThrownBy(() -> StompServerFanoutLauncher.resolveBackupDirectory(backupSettings))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("backup.path must be a directory");
    }

    @Test
    void empty_backup_path_uses_default_directory() {
        Settings.BackupSettings backupSettings = Settings.BackupSettings.fromConfig(
                true,
                "aof",
                "",
                "no",
                null
        );

        assertThat(StompServerFanoutLauncher.resolveBackupDirectory(backupSettings)).isNull();
    }
}
