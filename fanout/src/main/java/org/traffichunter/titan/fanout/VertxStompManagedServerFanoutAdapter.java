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
package org.traffichunter.titan.fanout;

import lombok.extern.slf4j.Slf4j;
import org.traffichunter.titan.bootstrap.GlobalShutdownHook;
import org.traffichunter.titan.core.message.dispatcher.DispatcherQueueManagers;
import org.traffichunter.titan.core.spi.ManagedServer;
import org.traffichunter.titan.core.spi.vertx.VertxStompManagedServer;
import org.traffichunter.titan.fanout.exporter.DispatchExporter;
import org.traffichunter.titan.fanout.exporter.VertxStompDispatchExporter;

import java.util.Map;
import java.util.function.Function;

/**
 * Fanout adapter for Vert.x's native STOMP managed server.
 *
 * @author yun
 */
@Slf4j
public final class VertxStompManagedServerFanoutAdapter implements ManagedServerFanoutAdapter {

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
