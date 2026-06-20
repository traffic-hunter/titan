package org.traffichunter.titan.monitor;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.traffichunter.titan.bootstrap.Settings;
import org.traffichunter.titan.core.spi.RuntimeLauncher;

class MonitorRuntimeLauncherTest {

    @Test
    void runtime_launcher_is_discoverable_by_service_loader() {
        List<RuntimeLauncher> launchers = RuntimeLauncher.load();

        assertThat(launchers)
                .anySatisfy(launcher -> assertThat(launcher).isInstanceOf(MonitorRuntimeLauncher.class));
    }

    @Test
    void runtime_launcher_stays_idle_when_monitor_is_disabled() {
        MonitorRuntimeLauncher launcher = new MonitorRuntimeLauncher();
        Settings settings = new Settings(List.of(), Settings.MonitorSettings.disabled(), null);

        assertThat(launcher.start(settings, List.of())).isEmpty();
    }
}
