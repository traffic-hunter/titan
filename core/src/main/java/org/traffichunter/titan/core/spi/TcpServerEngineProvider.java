/*
 * The MIT License
 *
 * Copyright (c) 2025 traffic-hunter
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.traffichunter.titan.core.spi;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.traffichunter.titan.bootstrap.ServerSettings;
import org.traffichunter.titan.core.channel.ChannelWriteBufferOption;
import org.traffichunter.titan.core.channel.ChannelInBoundHandler;
import org.traffichunter.titan.core.channel.ChannelOutBoundHandler;
import org.traffichunter.titan.core.channel.EventLoopGroups;
import org.traffichunter.titan.core.codec.LineFrameChannelDecoder;
import org.traffichunter.titan.core.net.TlsContextFactory;
import org.traffichunter.titan.core.transport.InetServer;
import org.traffichunter.titan.core.transport.option.InetServerOption;
import org.traffichunter.titan.core.util.OptionValues;

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
    public ManagedServer create(final ServerSettings settings) {
        EventLoopGroups groups = EventLoopGroups.group(settings.primaryThreads(), settings.secondaryThreads());
        InetServerOption inetOption = buildOption(settings.resolvedTransportOptions());
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

    private static InetServerOption buildOption(final Map<String, String> options) {
        OptionValues values = OptionValues.of(options);
        InetServerOption.Builder builder = InetServerOption.builder()
                .reuseAddress(values.getOrDefault("reuse-address", Boolean.class, true))
                .childTcpNoDelay(values.getOrDefault("child-tcp-no-delay", Boolean.class, true))
                .childKeepAlive(values.getOrDefault("child-keep-alive", Boolean.class, false))
                .childReuseAddress(values.getOrDefault("child-reuse-address", Boolean.class, true))
                .childWriteBuffer(
                        values.getOrDefault("max-pending-bytes", Integer.class,
                                ChannelWriteBufferOption.DEFAULT_MAX_PENDING_BYTES),
                        values.getOrDefault("high-watermark-bytes", Integer.class,
                                ChannelWriteBufferOption.DEFAULT_HIGH_WATERMARK_BYTES),
                        values.getOrDefault("low-watermark-bytes", Integer.class,
                                ChannelWriteBufferOption.DEFAULT_LOW_WATERMARK_BYTES)
                );

        Integer receiveBufferSize = values.get("receive-buffer-size", Integer.class);
        Integer childSendBufferSize = values.get("child-send-buffer-size", Integer.class);
        Integer childReceiveBufferSize = values.get("child-receive-buffer-size", Integer.class);

        if (receiveBufferSize != null) {
            builder.receiveBufferSize(receiveBufferSize);
        }
        if (childSendBufferSize != null) {
            builder.childSendBufferSize(childSendBufferSize);
        }
        if (childReceiveBufferSize != null) {
            builder.childReceiveBufferSize(childReceiveBufferSize);
        }
        return builder.build();
    }
}
