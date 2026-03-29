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
import org.traffichunter.titan.bootstrap.ServerSettings;
import org.traffichunter.titan.core.channel.EventLoopGroups;
import org.traffichunter.titan.core.codec.LineFrameChannelDecoder;
import org.traffichunter.titan.core.transport.InetServer;
import org.traffichunter.titan.core.transport.option.InetServerOption;

public final class TcpServerEngineProvider implements NetworkServerEngineProvider {

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
        InetServer server = InetServer.builder()
                .group(groups)
                .options(inetOption)
                .channelHandler(channel -> channel.chain()
                        .add(new LineFrameChannelDecoder())
                )
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

    private static Integer intOption(final Map<String, String> options, final String key) {
        String value = options.get(key);
        return value == null || value.isBlank() ? null : Integer.parseInt(value);
    }

    private static boolean booleanOption(final Map<String, String> options, final String key, final boolean defaultValue) {
        String value = options.get(key);
        return value == null || value.isBlank() ? defaultValue : Boolean.parseBoolean(value);
    }
}
