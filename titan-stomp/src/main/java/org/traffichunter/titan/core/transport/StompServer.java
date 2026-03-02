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
package org.traffichunter.titan.core.transport;

import java.net.InetSocketAddress;
import java.net.SocketOption;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.traffichunter.titan.core.channel.EventLoopGroups;
import org.traffichunter.titan.core.channel.NetChannel;
import org.traffichunter.titan.core.channel.NetServerChannel;
import org.traffichunter.titan.core.channel.stomp.StompHandler;
import org.traffichunter.titan.core.channel.stomp.StompNetChannel;
import org.traffichunter.titan.core.channel.stomp.StompNetServerChannel;
import org.traffichunter.titan.core.channel.stomp.StompServerHandler;
import org.traffichunter.titan.core.codec.stomp.StompChannelDecoder;
import org.traffichunter.titan.core.codec.stomp.StompVersion;
import org.traffichunter.titan.core.concurrent.Promise;
import org.traffichunter.titan.core.dispatcher.Dispatcher;
import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.transport.option.InetServerOption;
import org.traffichunter.titan.core.util.Assert;
import org.traffichunter.titan.core.util.Handler;
import org.traffichunter.titan.core.util.buffer.Buffer;

/**
 * @author yun
 */
public final class StompServer extends AbstractTransport<StompNetServerChannel> {

    private final InetServer inetServer;
    private final Map<String, StompNetChannel> childChannels;

    private StompServer(InetServer inetServer, Map<String, StompNetChannel> childChannels, StompNetServerChannel channel, EventLoopGroups eventLoopGroups) {
        super(channel, eventLoopGroups);
        this.inetServer = inetServer;
        this.childChannels = childChannels;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public void start() {
        inetServer.start();
    }

    public Promise<Void> listen(String host, int port) {
        return inetServer.listen(host, port);
    }

    public Promise<Void> listen(InetSocketAddress address) {
        return inetServer.listen(address);
    }

    public Promise<Void> send(Buffer buffer) {
        return inetServer.send(buffer);
    }

    public Promise<Void> sendFrame(Buffer frameBuffer) {
        return inetServer.send(frameBuffer);
    }

    public @Nullable StompNetChannel findChannel(String channelId) {
        return childChannels.get(channelId);
    }

    public void shutdown() {
        shutdown(30, TimeUnit.SECONDS);
    }

    public void shutdown(long timeout, TimeUnit unit) {
        inetServer.shutdown(timeout, unit);
    }

    public static final class Builder {

        private @Nullable EventLoopGroups groups;
        private StompVersion version = StompVersion.STOMP_1_2;

        private Consumer<NetChannel> childOptionApplier = channel -> { };
        private Consumer<NetServerChannel> serverOptionApplier = channel -> { };
        private @Nullable Handler<StompNetChannel> stompChannelHandler;
        private @Nullable StompHandler inboundHandler;

        public Builder group(EventLoopGroups groups) {
            this.groups = groups;
            return this;
        }

        public Builder version(StompVersion version) {
            this.version = version;
            return this;
        }

        public <T> Builder option(SocketOption<T> option, T value) {
            serverOptionApplier = serverOptionApplier.andThen(channel -> channel.setOption(option, value));
            return this;
        }

        public <T> Builder childOption(SocketOption<T> option, T value) {
            childOptionApplier = childOptionApplier.andThen(channel -> channel.setOption(option, value));
            return this;
        }

        public Builder option(Consumer<NetServerChannel> optionApplier) {
            this.serverOptionApplier = this.serverOptionApplier.andThen(optionApplier);
            return this;
        }

        public Builder childOption(Consumer<NetChannel> optionApplier) {
            this.childOptionApplier = this.childOptionApplier.andThen(optionApplier);
            return this;
        }

        @SuppressWarnings("unchecked")
        public Builder options(InetServerOption option) {
            this.serverOptionApplier = this.serverOptionApplier.andThen(channel ->
                    option.serverSocketOptions().forEach((k, v) ->
                            channel.setOption((SocketOption<Object>) k, v)
                    ));
            this.childOptionApplier = this.childOptionApplier.andThen(channel ->
                    option.childSocketOptions().forEach((k, v) ->
                            channel.setOption((SocketOption<Object>) k, v)
                    ));
            return this;
        }

        public Builder channelHandler(Handler<StompNetChannel> stompChannelHandler) {
            this.stompChannelHandler = stompChannelHandler;
            return this;
        }

        public StompServer build() {
            Assert.checkNotNull(groups, "groups cannot be null");

            Map<String, StompNetChannel> channels = new HashMap<>();

            InetServer inetServer = InetServer.builder()
                    .group(groups)
                    .option(serverOptionApplier)
                    .channelOption(childOptionApplier)
                    .channelHandler(channel -> {
                        if (!(channel instanceof NetChannel netChannel)) {
                            throw new IllegalArgumentException("Unsupported channel: " + channel);
                        }

                        StompNetChannel stompChannel = StompNetChannel.open(netChannel, version);
                        channels.put(stompChannel.id(), stompChannel);
                        netChannel.chain().add(new StompChannelDecoder(stompChannel));

                        if (stompChannelHandler != null) {
                            stompChannelHandler.handle(stompChannel);
                        }
                    })
                    .build();

            StompNetServerChannel stompNetServerChannel = StompNetServerChannel.open(inetServer.channel(), version);

            return new StompServer(inetServer, Collections.unmodifiableMap(channels), stompNetServerChannel, groups);
        }
    }
}
