/*
The MIT License

Copyright (c) 2025 traffic-hunter

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
*/
package org.traffichunter.titan.client;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.TimeUnit;

import org.traffichunter.titan.core.channel.EventLoopGroups;
import org.traffichunter.titan.core.message.dispatcher.Dispatcher;
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
                .maxBodyLength(configuration.maxFrameLength())
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
