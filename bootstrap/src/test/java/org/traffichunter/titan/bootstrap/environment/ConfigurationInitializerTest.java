package org.traffichunter.titan.bootstrap.environment;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.traffichunter.titan.bootstrap.ServerSettings;
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
        assertThat(settings.servers().getFirst().tls().enabled()).isFalse();
    }

    @Test
    void load_maps_tls_transport_settings() {
        String yaml = """
                titan:
                  servers:
                    - name: secure-stomp
                      protocol: stomp
                      transport: tcp
                      host: localhost
                      port: 61614
                      options:
                        reuse-address: "true"
                      transport-options:
                        receive-buffer-size: "65536"
                      tls:
                        side: server
                        versions:
                          - TLS_1_3
                          - TLS_1_2
                        client-auth: need
                        path: /etc/titan/server.p12
                        type: PKCS12
                        store-password: store-secret
                        key-password: key-secret
                        verify-hostname: false
                """;

        Settings settings = ConfigurationInitializer.getDefault("unused")
                .load(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));

        assertThat(settings.servers()).hasSize(1);
        ServerSettings server = settings.servers().getFirst();
        assertThat(server.name()).isEqualTo("secure-stomp");
        assertThat(server.transport()).isEqualTo("tcp");
        assertThat(server.protocol()).isEqualTo("stomp");
        assertThat(server.host()).isEqualTo("localhost");
        assertThat(server.port()).isEqualTo(61614);
        assertThat(server.resolvedTransportOptions())
                .containsEntry("reuse-address", "true")
                .containsEntry("receive-buffer-size", "65536")
                .doesNotContainKeys("tls-side", "tls-path", "tls-type");
        assertThat(server.tls().enabled()).isTrue();
        assertThat(server.tls().side()).isEqualTo("server");
        assertThat(server.tls().versions()).containsExactly("TLS_1_3", "TLS_1_2");
        assertThat(server.tls().clientAuth()).isEqualTo("need");
        assertThat(server.tls().path()).isEqualTo("/etc/titan/server.p12");
        assertThat(server.tls().type()).isEqualTo("PKCS12");
        assertThat(server.tls().storePassword()).isEqualTo("store-secret");
        assertThat(server.tls().keyPassword()).isEqualTo("key-secret");
        assertThat(server.tls().verifyHostname()).isFalse();
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
