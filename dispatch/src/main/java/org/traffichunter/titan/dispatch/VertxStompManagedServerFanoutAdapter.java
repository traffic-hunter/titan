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
package org.traffichunter.titan.dispatch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traffichunter.titan.bootstrap.GlobalShutdownHook;
import org.traffichunter.titan.core.spi.ManagedServer;
import org.traffichunter.titan.core.spi.vertx.VertxStompManagedServer;
import org.traffichunter.titan.dispatch.exporter.DispatchExporter;
import org.traffichunter.titan.dispatch.exporter.VertxStompDispatchExporter;

import java.util.Map;
import java.util.function.Function;

/**
 * Fanout adapter for Vert.x's native STOMP managed server.
 *
 * @author yun
 */
public final class VertxStompManagedServerFanoutAdapter implements ManagedServerFanoutAdapter {

    private static final Logger log = LoggerFactory.getLogger(VertxStompManagedServerFanoutAdapter.class);

    private static final GlobalShutdownHook SHUTDOWN_HOOK = GlobalShutdownHook.INSTANCE;

    @Override
    public boolean supports(
            String protocol,
            String transport,
            Map<String, String> protocolOptions,
            ManagedServer managedServer
    ) {
        return managedServer instanceof VertxStompManagedServer
                && "stomp".equalsIgnoreCase(protocol)
                && "vertx".equalsIgnoreCase(transport);
    }

    @Override
    public void apply(
            String protocol,
            String transport,
            Map<String, String> protocolOptions,
            ManagedServer managedServer,
            Function<DispatchExporter, DispatchGateway> gatewayFactory
    ) {
        VertxStompManagedServer vertxStompManagedServer = (VertxStompManagedServer) managedServer;
        DispatchGateway dispatchGateway = gatewayFactory.apply(
                new VertxStompDispatchExporter(vertxStompManagedServer.server())
        );
        SHUTDOWN_HOOK.addShutdownCallback(() -> {
            try {
                dispatchGateway.close();
            } catch (Exception e) {
                log.warn("Failed to close fanout gateway", e);
            }
        });

        DispatcherQueueManagers.register(managedServer.name(), dispatchGateway);

        vertxStompManagedServer.server().stompHandler()
                .sendHandler(new VertxStompSendToFanoutHandler(dispatchGateway));

        log.info("Fanout adapter installed for server={}", managedServer.name());
    }
}
