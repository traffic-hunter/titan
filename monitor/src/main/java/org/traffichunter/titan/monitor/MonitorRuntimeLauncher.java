package org.traffichunter.titan.monitor;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traffichunter.titan.bootstrap.Settings;
import org.traffichunter.titan.core.spi.ManagedServer;
import org.traffichunter.titan.core.spi.RuntimeLauncher;
import org.traffichunter.titan.monitor.http.MonitoringHttpServer;

public final class MonitorRuntimeLauncher implements RuntimeLauncher {

    private static final Logger log = LoggerFactory.getLogger(MonitorRuntimeLauncher.class);

    @Override
    public List<AutoCloseable> start(Settings settings, List<ManagedServer> managedServers) {
        Settings.MonitorSettings monitor = settings.monitor();
        if (!monitor.enabled()) {
            return List.of();
        }

        MonitoringHttpServer server = MonitoringHttpServer.builder(new MonitoringSnapshotService(version()))
                .host(monitor.host())
                .port(monitor.port())
                .threadPoolSize(monitor.threadPoolSize())
                .token(monitor.token())
                .build();
        Thread thread = new Thread(server::start, "titan-monitor-http");
        thread.setDaemon(true);
        thread.start();
        log.info("Started Titan monitor HTTP server at {}:{}", monitor.host(), monitor.port());
        return List.of(server::close);
    }

    private static String version() {
        Package pkg = MonitorRuntimeLauncher.class.getPackage();
        if (pkg == null) {
            return "unknown";
        }
        String version = pkg.getImplementationVersion();
        return version == null || version.isBlank() ? "unknown" : version;
    }
}
