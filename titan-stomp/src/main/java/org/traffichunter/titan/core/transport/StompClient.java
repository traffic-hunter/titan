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
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.channel.EventLoopGroups;
import org.traffichunter.titan.core.channel.NetChannel;
import org.traffichunter.titan.core.channel.stomp.StompHandler;
import org.traffichunter.titan.core.channel.stomp.StompNetChannel;
import org.traffichunter.titan.core.codec.stomp.StompChannelDecoder;
import org.traffichunter.titan.core.codec.stomp.StompFrame;
import org.traffichunter.titan.core.codec.stomp.StompVersion;
import org.traffichunter.titan.core.concurrent.Promise;
import org.traffichunter.titan.core.transport.option.InetClientOption;
import org.traffichunter.titan.core.util.Assert;
import org.traffichunter.titan.core.util.Handler;
import org.traffichunter.titan.core.util.buffer.Buffer;

/**
 * @author yun
 */
public final class StompClient extends AbstractTransport<StompNetChannel> {

    private final InetClient inetClient;

    private StompClient(InetClient inetClient, StompNetChannel channel, EventLoopGroups eventLoopGroups) {
        super(channel, eventLoopGroups);
        this.inetClient = inetClient;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public void start() {
        inetClient.start();
    }

    public Promise<Void> connect(String host, int port) {
        return inetClient.connect(host, port);
    }

    public Promise<Void> connect(InetSocketAddress remoteAddress) {
        return inetClient.connect(remoteAddress);
    }

    @Override
    public Promise<Void> send(Buffer buffer) {
        return inetClient.send(buffer);
    }

    public Promise<Void> send(StompFrame frame) {
        return inetClient.send(frame.toBuffer());
    }

    public void shutdown() {
        shutdown(30, TimeUnit.SECONDS);
    }

    @Override
    public void shutdown(long timeout, TimeUnit unit) {
        inetClient.shutdown(timeout, unit);
    }

    public static final class Builder {

        private @Nullable EventLoopGroups groups;
        private StompVersion version = StompVersion.STOMP_1_2;

        private Consumer<NetChannel> optionApplier = channel -> { };
        private @Nullable Handler<StompNetChannel> stompChannelHandler;

        public Builder group(EventLoopGroups groups) {
            this.groups = groups;
            return this;
        }

        public Builder version(StompVersion version) {
            this.version = version;
            return this;
        }

        public <T> Builder option(SocketOption<T> option, T value) {
            optionApplier = optionApplier.andThen(channel -> channel.setOption(option, value));
            return this;
        }

        public Builder option(Consumer<NetChannel> optionApplier) {
            this.optionApplier = this.optionApplier.andThen(optionApplier);
            return this;
        }

        @SuppressWarnings("unchecked")
        public Builder options(InetClientOption option) {
            this.optionApplier = this.optionApplier.andThen(channel ->
                    option.socketOptions().forEach((k, v) ->
                            channel.setOption((SocketOption<Object>) k, v)
                    ));
            return this;
        }

        public Builder channelHandler(Handler<StompNetChannel> stompChannelHandler) {
            this.stompChannelHandler = stompChannelHandler;
            return this;
        }

        public StompClient build() {
            Assert.checkNotNull(groups, "groups cannot be null");

            InetClient inetClient = InetClient.builder()
                    .group(groups)
                    .channelHandler(channel -> { })
                    .option(optionApplier)
                    .build();

            NetChannel netChannel = inetClient.channel();
            StompNetChannel stompChannel = StompNetChannel.open(netChannel, version);

            netChannel.chain().add(new StompChannelDecoder(stompChannel));
            if (stompChannelHandler != null) {
                stompChannelHandler.handle(stompChannel);
            }

            return new StompClient(inetClient, stompChannel, groups);
        }
    }
}
