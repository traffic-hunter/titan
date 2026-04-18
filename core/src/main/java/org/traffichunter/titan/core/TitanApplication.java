/*
 * The MIT License
 *
 * Copyright (c) 2025 traffic-hunter
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.traffichunter.titan.core;

import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;
import org.traffichunter.titan.bootstrap.GlobalShutdownHook;
import org.traffichunter.titan.bootstrap.TitanBootstrap.ApplicationStarter;
import org.traffichunter.titan.bootstrap.Settings;
import org.traffichunter.titan.core.spi.ManagedServer;
import org.traffichunter.titan.core.spi.FanoutLauncher;
import org.traffichunter.titan.core.spi.NetworkServerEngineProvider;

/**
 * Reflection entrypoint loaded by bootstrap.
 */
@Slf4j
@NullMarked
@SuppressWarnings("unused")
public class TitanApplication implements ApplicationStarter {

    private static final GlobalShutdownHook SHUTDOWN_HOOK = GlobalShutdownHook.INSTANCE;

    static {
        if(!SHUTDOWN_HOOK.isEnabled()) {
            SHUTDOWN_HOOK.enableShutdownHook();
        }
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

        managedServers.forEach(managedServer ->
                SHUTDOWN_HOOK.addShutdownCallback(managedServer::stop)
        );
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
