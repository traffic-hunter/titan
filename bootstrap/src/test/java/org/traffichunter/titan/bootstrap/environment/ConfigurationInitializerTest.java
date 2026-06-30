package org.traffichunter.titan.bootstrap.environment;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.traffichunter.titan.bootstrap.Settings;

class ConfigurationInitializerTest {

    @Test
    void load_maps_monitor_settings() {
        String yaml = """
                titan:
                  monitor:
                    enabled: true
                    host: 127.0.0.1
                    port: 8777
                    token: secret
                    thread-pool-size: 12
                  servers:
                    - name: stomp
                      protocol: stomp
                      transport: nio
                      port: 7777
                """;

        Settings settings = ConfigurationInitializer.getDefault("unused")
                .load(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));

        assertThat(settings.monitor().enabled()).isTrue();
        assertThat(settings.monitor().host()).isEqualTo("127.0.0.1");
        assertThat(settings.monitor().port()).isEqualTo(8777);
        assertThat(settings.monitor().token()).isEqualTo("secret");
        assertThat(settings.monitor().threadPoolSize()).isEqualTo(12);
    }

    @Test
    void load_maps_backup_settings() {
        String yaml = """
                titan:
                  backup:
                    enabled: true
                    type: aof
                    path: ./data/backup
                    sync-policy: every
                    recovery-policy: fail-on-truncated-tail
                """;

        Settings settings = ConfigurationInitializer.getDefault("unused")
                .load(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));

        assertThat(settings.backup().enabled()).isTrue();
        assertThat(settings.backup().type()).isEqualTo("aof");
        assertThat(settings.backup().path()).isEqualTo("./data/backup");
        assertThat(settings.backup().syncPolicy()).isEqualTo("every");
        assertThat(settings.backup().recoveryPolicy()).isEqualTo("fail-on-truncated-tail");
    }

    @Test
    void load_defaults_monitor_to_disabled_when_missing() {
        String yaml = """
                titan:
                  servers:
                    - name: stomp
                      protocol: stomp
                      transport: nio
                      port: 7777
                """;

        Settings settings = ConfigurationInitializer.getDefault("unused")
                .load(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));

        assertThat(settings.monitor().enabled()).isFalse();
        assertThat(settings.monitor().host()).isEqualTo("127.0.0.1");
        assertThat(settings.monitor().port()).isEqualTo(7777);
        assertThat(settings.backup().enabled()).isFalse();
        assertThat(settings.backup().type()).isEqualTo("aof");
        assertThat(settings.backup().syncPolicy()).isEqualTo("every_sec");
        assertThat(settings.backup().recoveryPolicy()).isEqualTo("load_truncated_tail");
    }

    @Test
    void settings_builder_defaults_missing_sections() {
        Settings settings = new Settings(null, null, null);

        assertThat(settings.servers()).isEmpty();
        assertThat(settings.monitor().enabled()).isFalse();
        assertThat(settings.monitor().host()).isEqualTo("127.0.0.1");
        assertThat(settings.monitor().port()).isEqualTo(7777);
        assertThat(settings.backup().enabled()).isFalse();
    }
}
