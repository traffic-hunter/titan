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
import java.util.stream.Stream;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import org.traffichunter.titan.bootstrap.ServerSettings;
import org.traffichunter.titan.core.channel.ChannelInBoundHandler;
import org.traffichunter.titan.core.channel.ChannelOutBoundHandler;
import org.traffichunter.titan.core.channel.EventLoopGroups;
import org.traffichunter.titan.core.net.TlsContextFactory;
import org.traffichunter.titan.core.transport.InetServer;
import org.traffichunter.titan.core.transport.option.InetServerOption;
import org.traffichunter.titan.core.transport.stomp.StompServer;
import org.traffichunter.titan.core.transport.stomp.option.StompServerOption;
import org.traffichunter.titan.core.util.OptionKey;

/**
 * Service provider for STOMP-over-TCP server engines.
 *
 * <p>The provider translates bootstrap protocol and transport options into
 * {@link StompServerOption} and {@link InetServerOption}, then installs any externally supplied
 * channel handlers on accepted STOMP child channels.</p>
 */
public class StompServerEngineProvider implements NetworkServerEngineProvider {

    private static final OptionKey<String> PATH = OptionKey.stringKey("path");
    private static final OptionKey<Integer> MAX_FRAME_LENGTH = OptionKey.intKey("max-frame-length");
    private static final OptionKey<Integer> MAX_FRAME_IN_TRANSACTION = OptionKey.intKey("max-frame-in-transaction");
    private static final OptionKey<String> SUPPORTED_VERSIONS = OptionKey.stringKey("supported-versions");
    private static final OptionKey<Long> HEARTBEAT_X = OptionKey.longKey("heartbeat-x");
    private static final OptionKey<Long> HEARTBEAT_Y = OptionKey.longKey("heartbeat-y");
    private static final OptionKey<Long> CONNECT_TIMEOUT_MILLIS = OptionKey.longKey("connect-timeout-millis");
    private static final OptionKey<Boolean> SECURED = OptionKey.booleanKey("secured");
    private static final OptionKey<Boolean> SEND_ERROR_ON_NO_SUBSCRIPTIONS =
            OptionKey.booleanKey("send-error-on-no-subscriptions");
    private static final OptionKey<Long> ACK_TIMEOUT_MILLIS = OptionKey.longKey("ack-timeout-millis");
    private static final OptionKey<Integer> TIME_FACTOR = OptionKey.intKey("time-factor");
    private static final OptionKey<Integer> TRANSACTION_CHUNK_SIZE = OptionKey.intKey("transaction-chunk-size");
    private static final OptionKey<Integer> MAX_SUBSCRIPTIONS_BY_CLIENT =
            OptionKey.intKey("max-subscriptions-by-client");

    private static final List<OptionKey<?>> OPTION_KEYS = Stream.concat(
            Stream.of(
                    PATH,
                    MAX_FRAME_LENGTH,
                    MAX_FRAME_IN_TRANSACTION,
                    SUPPORTED_VERSIONS,
                    HEARTBEAT_X,
                    HEARTBEAT_Y,
                    CONNECT_TIMEOUT_MILLIS,
                    SECURED,
                    SEND_ERROR_ON_NO_SUBSCRIPTIONS,
                    ACK_TIMEOUT_MILLIS,
                    TIME_FACTOR,
                    TRANSACTION_CHUNK_SIZE,
                    MAX_SUBSCRIPTIONS_BY_CLIENT
            ),
            InetServerOptionKeys.ALL.stream()
    ).toList();

    private final boolean webSocket;

    private final List<ChannelInBoundHandler> inboundHandlers = new ArrayList<>();
    private final List<ChannelOutBoundHandler> outboundHandlers = new ArrayList<>();

    public StompServerEngineProvider() {
        this(false);
    }

    protected StompServerEngineProvider(boolean webSocket) {
        this.webSocket = webSocket;
    }

    /**
     * Adds the handler after the STOMP decoder. The decoder hands frames to the server handler
     * and passes no frames on, so this handler never receives STOMP frames.
     */
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
    public List<OptionKey<?>> optionKeys() {
        return OPTION_KEYS;
    }

    @Override
    public ManagedServer create(final ServerSettings settings) {
        Map<String, String> transportOptions = settings.resolvedTransportOptions();
        InetServerOption inetOption = InetServerOptionKeys.inetServerOption(transportOptions);
        StompServerOption stompServerOption = buildOption(settings.resolvedProtocolOptions(), inetOption);
        String path = PATH.get(transportOptions, "/");

        EventLoopGroups groups = EventLoopGroups.group(settings.primaryThreads(), settings.secondaryThreads());
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
                .maxFrameLength(MAX_FRAME_LENGTH.get(options))
                .maxFrameInTransaction(MAX_FRAME_IN_TRANSACTION.get(options))
                .supportedVersions(SUPPORTED_VERSIONS.get(options))
                .heartbeatX(HEARTBEAT_X.get(options))
                .heartbeatY(HEARTBEAT_Y.get(options))
                .connectTimeoutMillis(CONNECT_TIMEOUT_MILLIS.get(options))
                .secured(SECURED.get(options))
                .sendErrorOnNoSubscriptions(SEND_ERROR_ON_NO_SUBSCRIPTIONS.get(options))
                .ackTimeoutMillis(ACK_TIMEOUT_MILLIS.get(options))
                .timeFactor(TIME_FACTOR.get(options))
                .transactionChunkSize(TRANSACTION_CHUNK_SIZE.get(options))
                .maxSubscriptionsByClient(MAX_SUBSCRIPTIONS_BY_CLIENT.get(options))
                .inetServerOption(inetOption)
                .build();
    }
}
