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
package org.traffichunter.titan.stability;

import java.io.Closeable;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

import org.traffichunter.titan.core.channel.EventLoopGroups;
import org.traffichunter.titan.core.transport.stomp.StompServer;
import org.traffichunter.titan.core.transport.stomp.option.StompServerOption;
import org.traffichunter.titan.dispatch.DispatchGateway;
import org.traffichunter.titan.dispatch.DispatchMode;
import org.traffichunter.titan.dispatch.Dispatcher;
import org.traffichunter.titan.dispatch.StompSendToFanoutHandler;
import org.traffichunter.titan.dispatch.exporter.StompDispatchExporter;
import org.traffichunter.titan.stability.StabilityFixtureOptions.DeliveryPath;
import org.traffichunter.titan.stability.StabilityFixtureOptions.Transport;

/**
 * A STOMP broker assembled from explicit settings for one stability run.
 *
 * <p>The delivery path is chosen here rather than discovered: with {@link DeliveryPath#DISPATCH}
 * the SEND ingress is the dispatch gateway, and with {@link DeliveryPath#DIRECT} the server keeps
 * its built-in best-effort fanout. The two make different promises, so a result from one is never
 * evidence about the other.</p>
 *
 * @author yun
 */
public final class StabilityFixture implements Closeable {

    private static final long LISTEN_TIMEOUT_SECONDS = 30;
    private static final long SHUTDOWN_TIMEOUT_SECONDS = 10;

    private final StabilityFixtureOptions options;
    private final StompServer server;
    private final DispatchGateway gateway;
    private final int port;

    private StabilityFixture(
            StabilityFixtureOptions options,
            StompServer server,
            DispatchGateway gateway,
            int port
    ) {
        this.options = options;
        this.server = server;
        this.gateway = gateway;
        this.port = port;
    }

    /**
     * Starts a broker and waits for it to accept connections.
     *
     * @param options the settings to apply exactly as given
     * @return the running fixture
     * @throws Exception when the server cannot start or bind
     */
    public static StabilityFixture start(StabilityFixtureOptions options) throws Exception {
        EventLoopGroups groups = EventLoopGroups.group(1, options.ioWorkers());
        StompServer server = StompServer.open(
                groups,
                StompServerOption.builder()
                        .maxFrameLength(options.maxFrameLength())
                        .build()
        );
        if (options.transport() == Transport.WEBSOCKET) {
            server.webSocket(options.webSocketPath());
        }

        DispatchGateway gateway = null;
        if (options.path() == DeliveryPath.DISPATCH) {
            Dispatcher dispatcher = Dispatcher.getDefault(
                    options.queueMaxPendingBytes(),
                    options.queueResumePendingBytes()
            );
            gateway = DispatchMode.resolveMode(options.dispatchMode().label())
                    .dispatchGateway(new StompDispatchExporter(server.connection()), dispatcher);
            StompSendToFanoutHandler sendHandler = new StompSendToFanoutHandler(gateway);
            server.onStomp(handler -> handler.sendHandler(sendHandler));
        }

        server.start();
        try {
            server.listen(options.host(), options.port()).get(LISTEN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception error) {
            closeQuietly(server, gateway);
            throw error;
        }

        int port = ((InetSocketAddress) server.connection().channel().localAddress()).getPort();
        return new StabilityFixture(options, server, gateway, port);
    }

    /** Returns the port the fixture actually bound, which the caller may have left the OS to pick. */
    public int port() {
        return port;
    }

    /** Returns what this fixture is running, ready to be written next to a run's results. */
    public FixtureManifest manifest() {
        return FixtureManifest.of(options, port);
    }

    @Override
    public void close() {
        closeQuietly(server, gateway);
    }

    private static void closeQuietly(StompServer server, DispatchGateway gateway) {
        try {
            if (gateway != null) {
                gateway.close();
            }
        } catch (Exception ignored) {
            // A gateway that refuses to close must not keep the server listening.
        }
        if (!server.isShutdown()) {
            server.shutdown(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }
}
