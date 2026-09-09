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
package org.traffichunter.titan.core.spi;

import org.traffichunter.titan.bootstrap.ServerSettings;
import org.traffichunter.titan.core.transport.stomp.StompServer;

import java.util.concurrent.TimeUnit;

/**
 * Managed lifecycle adapter for a STOMP server.
 *
 * <p>The SPI exposes this wrapper to bootstrap code while keeping STOMP-specific access
 * available through {@link #server()} for integrations that need it.</p>
 *
 * @author yun
 */
public final class StompManagedServer implements ManagedServer {

    private final StompServer server;
    private final ServerSettings settings;

    public StompManagedServer(StompServer server, ServerSettings settings) {
        this.server = server;
        this.settings = settings;
    }

    @Override
    public String name() {
        return settings.serverName();
    }

    @Override
    public void start() {
        try {
            server.start();
            server.listen(settings.host(), settings.port()).get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to start STOMP server " + name(), e);
        }
    }

    public StompServer server() {
        return server;
    }

    @Override
    public void stop() {
        server.shutdown();
    }
}
