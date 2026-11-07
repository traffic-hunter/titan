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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.traffichunter.titan.bootstrap.ServerSettings;
import org.traffichunter.titan.core.channel.ChannelInBoundHandler;
import org.traffichunter.titan.core.channel.ChannelOutBoundHandler;
import org.traffichunter.titan.core.channel.EventLoopGroups;
import org.traffichunter.titan.core.codec.LineFrameChannelDecoder;
import org.traffichunter.titan.core.net.TlsContextFactory;
import org.traffichunter.titan.core.transport.InetServer;
import org.traffichunter.titan.core.transport.option.InetServerOption;
import org.traffichunter.titan.core.util.OptionKey;

/**
 * Built-in provider for plain TCP line-frame servers.
 *
 * <p>The provider builds an {@link InetServer}, installs the line-frame decoder, and then
 * appends handlers contributed by bootstrap integrations.</p>
 */
public final class TcpServerEngineProvider implements NetworkServerEngineProvider {

    private final List<ChannelInBoundHandler> inboundHandlers = new ArrayList<>();
    private final List<ChannelOutBoundHandler> outboundHandlers = new ArrayList<>();

    @Override
    public NetworkServerEngineProvider setInboundHandler(ChannelInBoundHandler channelInBoundHandler) {
        inboundHandlers.add(channelInBoundHandler);
        return this;
    }

    @Override
    public NetworkServerEngineProvider setOutboundHandler(ChannelOutBoundHandler channelOutBoundHandler) {
        outboundHandlers.add(channelOutBoundHandler);
        return this;
    }

    @Override
    public String transport() {
        return "tcp";
    }

    @Override
    public String protocol() {
        return "tcp";
    }

    @Override
    public List<OptionKey<?>> optionKeys() {
        return InetServerOptionKeys.ALL;
    }

    @Override
    public ManagedServer create(final ServerSettings settings) {
        InetServerOption inetOption = InetServerOptionKeys.inetServerOption(settings.resolvedTransportOptions());
        EventLoopGroups groups = EventLoopGroups.group(settings.primaryThreads(), settings.secondaryThreads());
        InetServer server = InetServer.open(groups).option(inetOption);
        if (settings.tls().enabled()) {
            server.tls(TlsContextFactory.create(settings.tls()));
        }
        server
                .onChannel(channel -> {
                    channel.chain().add(new LineFrameChannelDecoder());
                    inboundHandlers.forEach(inboundHandler ->
                            channel.chain().add(inboundHandler)
                    );
                    outboundHandlers.forEach(outboundHandler ->
                            channel.chain().add(outboundHandler)
                    );
                });

        return new ManagedServer() {
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
                    throw new IllegalStateException("Failed to start TCP server " + name(), e);
                }
            }

            @Override
            public void stop() {
                server.shutdown();
            }
        };
    }
}
