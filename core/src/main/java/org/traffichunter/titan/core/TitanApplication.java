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
package org.traffichunter.titan.core;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jspecify.annotations.NullMarked;
import org.traffichunter.titan.bootstrap.GlobalShutdownHook;
import org.traffichunter.titan.bootstrap.TitanBootstrap.ApplicationStarter;
import org.traffichunter.titan.bootstrap.Settings;
import org.traffichunter.titan.core.spi.ManagedServer;
import org.traffichunter.titan.core.spi.FanoutLauncher;
import org.traffichunter.titan.core.spi.NetworkServerEngineProvider;
import org.traffichunter.titan.core.spi.RuntimeLauncher;

/**
 * Core runtime entry point invoked by {@link org.traffichunter.titan.bootstrap.TitanBootstrap}.
 *
 * <p>Bootstrap calls {@code TitanApplication} after loading and normalizing external configuration.
 * The {@link ApplicationStarter} contract lets bootstrap start the core runtime without
 * compile-time dependencies on transport, protocol, or fanout implementations.</p>
 *
 * <p>For each configured server, SPI providers resolve its transport and protocol.
 * Matching launchers install optional fanout support. The application then starts
 * the managed server and registers it for cleanup on process shutdown.</p>
 */
@NullMarked
@SuppressWarnings("unused")
public class TitanApplication implements ApplicationStarter {

    private static final Logger log = LoggerFactory.getLogger(TitanApplication.class);

    private static final GlobalShutdownHook SHUTDOWN_HOOK = GlobalShutdownHook.INSTANCE;

    private final AtomicReference<Status> lifeCycle = new AtomicReference<>(Status.INITIALIZING);

    static {
        if(!SHUTDOWN_HOOK.isEnabled()) {
            SHUTDOWN_HOOK.enableShutdownHook();
        }
    }

    enum Status {
        INITIALIZING,
        RESTORING,
        RUNNING,
        STOPPING,
        STOPPED,
        FAILED,
        ;

    }

    @Override
    public void start(final Settings settings) {
        List<ManagedServer> managedServers = new ArrayList<>();
        List<FanoutLauncher> fanoutLaunchers = FanoutLauncher.load();

        settings.servers().forEach(serverSettings -> {
            ManagedServer server = NetworkServerEngineProvider
                    .find(serverSettings)
                    .create(serverSettings);

            fanoutLaunchers.stream()
                    .filter(plugin -> plugin.supports(
                            serverSettings.protocol(),
                            serverSettings.transport(),
                            serverSettings.resolvedProtocolOptions(),
                            server
                    ))
                    .forEach(plugin -> plugin.apply(
                            settings,
                            serverSettings.protocol(),
                            serverSettings.transport(),
                            serverSettings.resolvedProtocolOptions(),
                            server
                    ));
            server.start();
            log.info("Started TitanServer at {}", server.name());
            managedServers.add(server);
        });

        if(managedServers.isEmpty()) {
            log.error("No managed servers found");
            throw new CoreApplicationException("No managed servers found");
        }

        RuntimeLauncher.load().forEach(launcher ->
                launcher.start(settings, List.copyOf(managedServers))
                        .forEach(closeable -> SHUTDOWN_HOOK.addShutdownCallback(() -> closeQuietly(closeable)))
        );

        managedServers.forEach(managedServer ->
                SHUTDOWN_HOOK.addShutdownCallback(managedServer::stop)
        );
    }

    private static void closeQuietly(AutoCloseable closeable) {
        try {
            closeable.close();
        } catch (Exception e) {
            log.warn("Failed to stop runtime extension cleanly", e);
        }
    }

    public static class CoreApplicationException extends RuntimeException {

        public CoreApplicationException() {
        }

        public CoreApplicationException(final String message) {
            super(message);
        }

        public CoreApplicationException(final String message, final Throwable cause) {
            super(message, cause);
        }

        public CoreApplicationException(final Throwable cause) {
            super(cause);
        }
    }
}
