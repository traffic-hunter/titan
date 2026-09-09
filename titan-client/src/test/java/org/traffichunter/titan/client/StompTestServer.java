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
package org.traffichunter.titan.client;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.TimeUnit;

import org.traffichunter.titan.core.channel.EventLoopGroups;
import org.traffichunter.titan.dispatch.Dispatcher;
import org.traffichunter.titan.core.transport.option.InetServerOption;
import org.traffichunter.titan.core.transport.stomp.StompServer;
import org.traffichunter.titan.core.transport.stomp.option.StompServerOption;

public final class StompTestServer implements AutoCloseable {

    private static final int LIFECYCLE_TIMEOUT_SECONDS = 3;

    private final EnableStompServer configuration;
    private final Dispatcher dispatcher = Dispatcher.getDefault();

    private int port;
    private StompServer server;

    public StompTestServer(EnableStompServer configuration) throws Exception {
        this.configuration = configuration;
        this.server = start(configuration.port());
    }

    public String host() {
        return configuration.host();
    }

    public int port() {
        return port;
    }

    public StompServer server() {
        return server;
    }

    public Dispatcher dispatcher() {
        return dispatcher;
    }

    public void stop() {
        if (!server.isShutdown()) {
            server.shutdown(LIFECYCLE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    public void restart() throws Exception {
        int restartPort = port;
        stop();
        server = start(restartPort);
    }

    @Override
    public void close() {
        stop();
    }

    private StompServer start(int bindPort) throws Exception {
        EventLoopGroups groups = EventLoopGroups.group(
                configuration.primaryThreads(),
                configuration.secondaryThreads()
        );
        InetServerOption inetOption = InetServerOption.builder()
                .reuseAddress(true)
                .childReuseAddress(true)
                .build();
        StompServerOption serverOption = StompServerOption.builder()
                .maxFrameLength(configuration.maxFrameLength())
                .inetServerOption(inetOption)
                .build();
        StompServer startedServer = StompServer.open(groups, serverOption);
        startedServer.start();
        startedServer.listen(host(), bindPort).get(LIFECYCLE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        SocketAddress localAddress = startedServer.connection().channel().localAddress();
        if (!(localAddress instanceof InetSocketAddress inetAddress)) {
            startedServer.shutdown(LIFECYCLE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            throw new IllegalStateException("STOMP test server has no local address");
        }
        port = inetAddress.getPort();
        return startedServer;
    }
}
