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

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.ext.stomp.StompServer;
import io.vertx.ext.stomp.StompServerHandler;
import io.vertx.ext.stomp.StompServerOptions;
import org.traffichunter.titan.bootstrap.ServerSettings;
import org.traffichunter.titan.core.spi.ManagedServer;

/**
 * Provides the legacy Vert.x WebSocket STOMP server transport.
 *
 * @deprecated use Titan's native WebSocket STOMP server transport; this provider is retained only
 * for migration compatibility
 *
 * @author yun
 */
@Deprecated(since = "0.9.0")
public final class VertxStompWebSocketServerEngineProvider extends VertxStompServerEngineProvider {

    @Override
    public String transport() {
        return "vertx-websocket";
    }

    @Override
    public ManagedServer create(ServerSettings settings) {
        Vertx vertx = Vertx.vertx(new VertxOptions()
                .setEventLoopPoolSize(settings.primaryThreads())
                .setWorkerPoolSize(settings.secondaryThreads()));
        StompServerOptions stompServerOptions = buildOption(
                settings.resolvedProtocolOptions(),
                settings.resolvedTransportOptions()
        ).setPort(settings.port())
                .setHost(settings.host())
                .setWebsocketBridge(true);

        StompServer stompServer = StompServer.create(vertx, stompServerOptions)
                .handler(StompServerHandler.create(vertx));

        return new VertxStompManagedServer(stompServer, settings);
    }
}
