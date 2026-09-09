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
package org.traffichunter.titan.core.spi.vertx;

import io.vertx.core.Future;
import io.vertx.ext.stomp.StompServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traffichunter.titan.bootstrap.ServerSettings;
import org.traffichunter.titan.core.spi.ManagedServer;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @author yun
 */
public final class VertxStompManagedServer implements ManagedServer {

    private static final Logger log = LoggerFactory.getLogger(VertxStompManagedServer.class);

    private final StompServer server;
    private final ServerSettings settings;

    public VertxStompManagedServer(StompServer server, ServerSettings settings) {
        this.server = server;
        this.settings = settings;
    }

    @Override
    public String name() {
        return settings.serverName();
    }

    public StompServer server() {
        return server;
    }

    @Override
    public void start() {
        try {
            await(server.listen());
            log.info("Started Vert.x STOMP server {} on {}:{}", name(), settings.host(), settings.port());
        } catch (Exception e) {
            stop();
            throw new IllegalStateException("Failed to start Vert.x STOMP server " + name(), e);
        }
    }

    @Override
    public void stop() {
        RuntimeException rex = null;

        try {
            if (server.isListening()) {
                await(server.close());
            }
        } catch (RuntimeException e) {
            rex = e;
        }

        try {
            await(server.close());
        } catch (RuntimeException e) {
            if (rex == null) {
                rex = e;
            }
        }

        if (rex != null) {
            throw new IllegalStateException("Failed to stop Vert.x STOMP server " + name(), rex);
        }
    }

    private static void await(Future<?> future) {
        try {
            future.await(30, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new IllegalStateException("Timed out waiting for Vert.x STOMP operation", e);
        }
    }
}
