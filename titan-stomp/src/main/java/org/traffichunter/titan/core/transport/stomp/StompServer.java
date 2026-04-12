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
package org.traffichunter.titan.core.transport.stomp;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import org.traffichunter.titan.core.channel.EventLoopGroups;
import org.traffichunter.titan.core.channel.NetChannel;
import org.traffichunter.titan.core.channel.stomp.StompClientConnection;
import org.traffichunter.titan.core.channel.stomp.StompServerConnection;
import org.traffichunter.titan.core.channel.stomp.StompServerHandler;
import org.traffichunter.titan.core.channel.stomp.StompServerHandlerImpl;
import org.traffichunter.titan.core.codec.stomp.StompChannelDecoder;
import org.traffichunter.titan.core.codec.stomp.StompException;
import org.traffichunter.titan.core.concurrent.ChannelPromise;
import org.traffichunter.titan.core.concurrent.Promise;
import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.transport.InetServer;
import org.traffichunter.titan.core.transport.option.InetServerOption;
import org.traffichunter.titan.core.transport.stomp.option.StompClientOption;
import org.traffichunter.titan.core.transport.stomp.option.StompServerOption;
import org.traffichunter.titan.core.util.Assert;
import org.traffichunter.titan.core.util.Handler;

/**
 * @author yun
 */
public final class StompServer {

    private final InetServer inetServer;
    private final StompServerOption option;
    private final StompServerConnection serverConnection;

    private StompServer(
            EventLoopGroups groups,
            StompServerOption option,
            InetServerOption inetServerOption,
            StompClientOption childOption,
            Handler<StompServerHandler> stompServerHandlerConfigurer
    ) {
        this.option = option;

        StompServerConnection stompServerConnection = StompServerConnection.create(option);
        StompClientOption acceptedConnectionOption = serverChildSessionOption(option, childOption);
        StompServerHandler stompServerHandler = new StompServerHandlerImpl(stompServerConnection);
        stompServerHandlerConfigurer.handle(stompServerHandler);

        InetServer stompInetServer = InetServer.builder()
                .group(groups)
                .options(inetServerOption)
                .channelHandler(channel -> {
                    if (!(channel instanceof NetChannel netChannel)) {
                        throw new IllegalArgumentException("Unsupported channel: " + channel);
                    }

                    StompClientConnection stompConnection =
                            StompClientConnection.wrap(netChannel, acceptedConnectionOption);
                    stompServerConnection.register(stompConnection);

                    netChannel.chain()
                            .add(new StompChannelDecoder(option.maxBodyLength(), stompConnection, stompServerHandler));
                })
                .build();

        stompServerConnection.bind(stompInetServer.channel());

        this.serverConnection = stompServerConnection;
        this.inetServer = stompInetServer;
    }

    public static Builder builder() {
        return new Builder();
    }

    public void start() {
        inetServer.start();
    }

    public Promise<Void> listen(String host, int port) {
        return listen(new InetSocketAddress(host, port));
    }

    public Promise<Void> listen(InetSocketAddress address) {
        ChannelPromise channelPromise = ChannelPromise.newPromise(serverConnection.channel());
        listen(address, channelPromise);
        return channelPromise;
    }

    public StompServerConnection connection() {
        return serverConnection;
    }

    public boolean isStart() {
        return inetServer.isStart();
    }

    public StompServerOption option() {
        return option;
    }

    public void shutdown() {
        shutdown(30, TimeUnit.SECONDS);
    }

    public void shutdown(long timeout, TimeUnit unit) {
        inetServer.shutdown(timeout, unit);
    }

    public boolean isShutdown() {
        return inetServer.isShutdown();
    }

    private void listen(InetSocketAddress address, ChannelPromise resultPromise) {
        inetServer.listen(address)
                .addListener(listenFuture -> {
                    if (listenFuture.isSuccess()) {
                        resultPromise.success();
                    } else {
                        resultPromise.fail(new StompException("Failed to listen on address " + address, listenFuture.error()));
                    }
                });
    }

    private static StompClientOption serverChildSessionOption(StompServerOption option, StompClientOption childOption) {
        return StompClientOption.builder()
                .version(option.stompVersion())
                .autoComputeContentLength(childOption.autoComputeContentLength())
                .heartbeatX(option.heartbeatX())
                .heartbeatY(option.heartbeatY())
                .maxFrameLength(option.maxBodyLength())
                .build();
    }

    public static final class Builder {

        private @Nullable EventLoopGroups groups;
        private @Nullable StompServerOption option;
        private InetServerOption inetServerOption = InetServerOption.DEFAULT_INET_SERVER_OPTION;
        private StompClientOption childOption = StompClientOption.DEFAULT_STOMP_CLIENT_OPTION;
        private Handler<StompServerHandler> stompServerHandler = handler -> {};

        @CanIgnoreReturnValue
        public Builder group(EventLoopGroups groups) {
            this.groups = groups;
            return this;
        }

        @CanIgnoreReturnValue
        public Builder option(StompServerOption option) {
            this.option = option;
            if (option.inetServerOption() != null) {
                this.inetServerOption = option.inetServerOption();
            }
            return this;
        }

        @CanIgnoreReturnValue
        public Builder option(InetServerOption option) {
            this.inetServerOption = option;
            return this;
        }

        @CanIgnoreReturnValue
        public Builder childOption(StompClientOption option) {
            this.childOption = option;
            return this;
        }

        public Builder handler(Handler<StompServerHandler> serverHandlerHandler) {
            this.stompServerHandler = serverHandlerHandler;
            return this;
        }

        public StompServer build() {
            Assert.checkNotNull(groups, "groups cannot be null");
            Assert.checkNotNull(option, "option cannot be null");

            return new StompServer(
                    groups,
                    option,
                    inetServerOption,
                    childOption,
                    stompServerHandler
            );
        }
    }
}
