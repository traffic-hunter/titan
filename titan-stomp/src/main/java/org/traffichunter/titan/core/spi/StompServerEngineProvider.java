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
import java.util.Map;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.bootstrap.ServerSettings;
import org.traffichunter.titan.core.channel.ChannelInBoundHandler;
import org.traffichunter.titan.core.channel.ChannelOutBoundHandler;
import org.traffichunter.titan.core.channel.EventLoopGroups;
import org.traffichunter.titan.core.net.TlsContextFactory;
import org.traffichunter.titan.core.transport.InetServer;
import org.traffichunter.titan.core.transport.option.InetServerOption;
import org.traffichunter.titan.core.transport.stomp.StompServer;
import org.traffichunter.titan.core.transport.stomp.option.StompServerOption;

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
        return StompServerOption.builder()
                .maxFrameLength(intOption(options, "max-frame-length"))
                .maxFrameInTransaction(intOption(options, "max-frame-in-transaction"))
                .supportedVersions(stringOption(options, "supported-versions"))
                .heartbeatX(longOption(options, "heartbeat-x"))
                .heartbeatY(longOption(options, "heartbeat-y"))
                .secured(booleanOption(options, "secured"))
                .sendErrorOnNoSubscriptions(booleanOption(options, "send-error-on-no-subscriptions"))
                .ackTimeoutMillis(longOption(options, "ack-timeout-millis"))
                .timeFactor(intOption(options, "time-factor"))
                .transactionChunkSize(intOption(options, "transaction-chunk-size"))
                .maxSubscriptionsByClient(intOption(options, "max-subscriptions-by-client"))
                .inetServerOption(inetOption)
                .build();
    }

    private static InetServerOption buildInetOption(final Map<String, String> options) {
        InetServerOption.Builder builder = InetServerOption.builder()
                .reuseAddress(booleanOption(options, "reuse-address", true))
                .childTcpNoDelay(booleanOption(options, "child-tcp-no-delay", true))
                .childKeepAlive(booleanOption(options, "child-keep-alive", false))
                .childReuseAddress(booleanOption(options, "child-reuse-address", true));

        Integer receiveBufferSize = intOption(options, "receive-buffer-size");
        Integer childSendBufferSize = intOption(options, "child-send-buffer-size");
        Integer childReceiveBufferSize = intOption(options, "child-receive-buffer-size");

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

    private static @Nullable Integer intOption(final Map<String, String> options, final String key) {
        String value = options.get(key);
        return value == null || value.isBlank() ? null : Integer.parseInt(value);
    }

    private static @Nullable Long longOption(final Map<String, String> options, final String key) {
        String value = options.get(key);
        return value == null || value.isBlank() ? null : Long.parseLong(value);
    }

    private static @Nullable Boolean booleanOption(final Map<String, String> options, final String key) {
        String value = options.get(key);
        return value == null || value.isBlank() ? null : Boolean.parseBoolean(value);
    }

    private static boolean booleanOption(final Map<String, String> options, final String key, final boolean defaultValue) {
        String value = options.get(key);
        return value == null || value.isBlank() ? defaultValue : Boolean.parseBoolean(value);
    }

    private static @Nullable String stringOption(final Map<String, String> options, final String key) {
        String value = options.get(key);
        return value == null || value.isBlank() ? null : value;
    }
}
