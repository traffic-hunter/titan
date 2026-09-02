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

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import org.traffichunter.titan.bootstrap.ServerSettings;
import org.traffichunter.titan.core.channel.ChannelInBoundHandler;
import org.traffichunter.titan.core.channel.ChannelOutBoundHandler;
import org.traffichunter.titan.core.channel.ChannelWriteBufferOption;
import org.traffichunter.titan.core.channel.EventLoopGroups;
import org.traffichunter.titan.core.net.TlsContextFactory;
import org.traffichunter.titan.core.transport.InetServer;
import org.traffichunter.titan.core.transport.option.InetServerOption;
import org.traffichunter.titan.core.transport.stomp.StompServer;
import org.traffichunter.titan.core.transport.stomp.option.StompServerOption;
import org.traffichunter.titan.core.util.OptionValues;

/**
 * Service provider for STOMP-over-TCP server engines.
 *
 * <p>The provider translates bootstrap protocol and transport options into
 * {@link StompServerOption} and {@link InetServerOption}, then installs any externally supplied
 * channel handlers on accepted STOMP child channels.</p>
 */
public class StompServerEngineProvider implements NetworkServerEngineProvider {

    private final boolean webSocket;

    private final List<ChannelInBoundHandler> inboundHandlers = new ArrayList<>();
    private final List<ChannelOutBoundHandler> outboundHandlers = new ArrayList<>();

    public StompServerEngineProvider() {
        this(false);
    }

    protected StompServerEngineProvider(boolean webSocket) {
        this.webSocket = webSocket;
    }

    @Override
    @CanIgnoreReturnValue
    public NetworkServerEngineProvider setInboundHandler(ChannelInBoundHandler channelInBoundHandler) {
        inboundHandlers.add(channelInBoundHandler);
        return this;
    }

    @Override
    @CanIgnoreReturnValue
    public NetworkServerEngineProvider setOutboundHandler(ChannelOutBoundHandler channelOutBoundHandler) {
        outboundHandlers.add(channelOutBoundHandler);
        return this;
    }

    @Override
    public ManagedServer create(final ServerSettings settings) {
        EventLoopGroups groups = EventLoopGroups.group(settings.primaryThreads(), settings.secondaryThreads());
        InetServerOption inetOption = buildInetOption(settings.resolvedTransportOptions());
        StompServerOption stompServerOption = buildOption(settings.resolvedProtocolOptions(), inetOption);

        String path = settings.resolvedTransportOptions().getOrDefault("path", "/");
        InetServer inetServer = InetServer.open(groups);
        if (settings.tls().enabled()) {
            inetServer.tls(TlsContextFactory.create(settings.tls()));
        }
        StompServer server = StompServer.open(groups, inetServer, stompServerOption);
        if (webSocket) {
            server.webSocket(path);
        }
        server.onChannel(channel -> {
                    inboundHandlers.forEach(inboundHandler ->
                            channel.chain().add(inboundHandler)
                    );
                    outboundHandlers.forEach(outboundHandler ->
                            channel.chain().add(outboundHandler)
                    );
                });

        return new StompManagedServer(server, settings);
    }

    @Override
    public String transport() {
        return webSocket ? "websocket" : "tcp";
    }

    @Override
    public String protocol() {
        return "stomp";
    }

    private static StompServerOption buildOption(final Map<String, String> options, final InetServerOption inetOption) {
        OptionValues values = OptionValues.of(options);
        return StompServerOption.builder()
                .maxFrameLength(values.get("max-frame-length", Integer.class))
                .maxFrameInTransaction(values.get("max-frame-in-transaction", Integer.class))
                .supportedVersions(values.get("supported-versions", String.class))
                .heartbeatX(values.get("heartbeat-x", Long.class))
                .heartbeatY(values.get("heartbeat-y", Long.class))
                .secured(values.get("secured", Boolean.class))
                .sendErrorOnNoSubscriptions(values.get("send-error-on-no-subscriptions", Boolean.class))
                .ackTimeoutMillis(values.get("ack-timeout-millis", Long.class))
                .timeFactor(values.get("time-factor", Integer.class))
                .transactionChunkSize(values.get("transaction-chunk-size", Integer.class))
                .maxSubscriptionsByClient(values.get("max-subscriptions-by-client", Integer.class))
                .inetServerOption(inetOption)
                .build();
    }

    private static InetServerOption buildInetOption(final Map<String, String> options) {
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
