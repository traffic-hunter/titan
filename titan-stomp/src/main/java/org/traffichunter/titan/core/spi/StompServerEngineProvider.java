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

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.bootstrap.ServerSettings;
import org.traffichunter.titan.core.channel.EventLoopGroups;
import org.traffichunter.titan.core.transport.option.InetServerOption;
import org.traffichunter.titan.core.transport.stomp.StompServer;
import org.traffichunter.titan.core.transport.stomp.option.StompServerOption;

public final class StompServerEngineProvider implements NetworkServerEngineProvider {

    @Override
    public String transport() {
        return "tcp";
    }

    @Override
    public String protocol() {
        return "stomp";
    }

    @Override
    public ManagedServer create(final ServerSettings settings) {
        EventLoopGroups groups = EventLoopGroups.group(settings.primaryThreads(), settings.secondaryThreads());
        InetServerOption inetOption = buildInetOption(settings.resolvedTransportOptions());
        StompServer server = StompServer.builder()
                .group(groups)
                .option(buildOption(settings.resolvedProtocolOptions(), inetOption))
                .build();

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
                    throw new IllegalStateException("Failed to start STOMP server " + name(), e);
                }
            }

            @Override
            public void stop() {
                server.shutdown();
            }
        };
    }

    private static StompServerOption buildOption(final Map<String, String> options, final InetServerOption inetOption) {
        return StompServerOption.builder()
                .maxHeaderLength(intOption(options, "max-header-length"))
                .maxHeaders(intOption(options, "max-headers"))
                .maxBodyLength(intOption(options, "max-body-length"))
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
