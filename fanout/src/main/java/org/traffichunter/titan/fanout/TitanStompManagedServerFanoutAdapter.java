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
import org.traffichunter.titan.core.spi.ManagedServer;
import org.traffichunter.titan.core.spi.StompManagedServer;
import org.traffichunter.titan.core.message.dispatcher.DispatcherQueueManagers;
import org.traffichunter.titan.fanout.exporter.FanoutExporter;
import org.traffichunter.titan.fanout.exporter.StompFanoutExporter;

import java.util.Map;
import java.util.function.Function;

/**
 * Fanout adapter for Titan's own STOMP managed server.
 *
 * @author yun
 */
@Slf4j
public final class TitanStompManagedServerFanoutAdapter implements ManagedServerFanoutAdapter {

    @Override
    public boolean supports(
            String protocol,
            String transport,
            Map<String, String> protocolOptions,
            ManagedServer managedServer
    ) {
        return managedServer instanceof StompManagedServer
                && "stomp".equalsIgnoreCase(protocol);
    }

    @Override
    public void apply(
            String protocol,
            String transport,
            Map<String, String> protocolOptions,
            ManagedServer managedServer,
            Function<FanoutExporter, FanoutGateway> gatewayFactory
    ) {
        StompManagedServer stompManagedServer = (StompManagedServer) managedServer;
        FanoutGateway fanoutGateway = gatewayFactory.apply(
                new StompFanoutExporter(stompManagedServer.server().connection())
        );
        DispatcherQueueManagers.register(managedServer.name(), fanoutGateway);

        stompManagedServer.server().onStomp(handler ->
                handler.sendHandler(new StompSendToFanoutHandler(fanoutGateway))
        );

        log.info("Fanout adapter installed for server={}", managedServer.name());
    }
}
