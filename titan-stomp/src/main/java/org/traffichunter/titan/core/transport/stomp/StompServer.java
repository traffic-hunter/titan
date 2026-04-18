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
import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.channel.Channel;
import org.traffichunter.titan.core.channel.EventLoopGroups;
import org.traffichunter.titan.core.channel.NetChannel;
import org.traffichunter.titan.core.channel.stomp.*;
import org.traffichunter.titan.core.codec.stomp.StompChannelDecoder;
import org.traffichunter.titan.core.codec.stomp.StompException;
import org.traffichunter.titan.core.concurrent.ChannelPromise;
import org.traffichunter.titan.core.concurrent.Promise;
import org.traffichunter.titan.core.transport.InetServer;
import org.traffichunter.titan.core.transport.stomp.option.StompClientOption;
import org.traffichunter.titan.core.transport.stomp.option.StompServerOption;
import org.traffichunter.titan.core.util.Assert;
import org.traffichunter.titan.core.util.Handler;

/**
 * @author yun
 */
public final class StompServer {

    private final InetServer inetServer;
    private final StompServerConnection serverConnection;
    private final StompServerOption option;

    private StompClientOption childOption = StompClientOption.DEFAULT_STOMP_CLIENT_OPTION;
    private Handler<StompServerHandler> stompServerHandler = handler -> {};
    private Handler<Channel> channelHandler = channel -> {};

    private StompServer(EventLoopGroups groups, @Nullable InetServer inetServer, StompServerOption option) {
        this.option = option;
        this.serverConnection = StompServerConnection.create(option);
        if(inetServer == null) {
            inetServer = initializeInetServer(groups);
        }
        this.inetServer = inetServer;
    }

    public static StompServer open(EventLoopGroups groups, StompServerOption option) {
        return open(groups, null, option);
    }

    public static StompServer open(EventLoopGroups groups, @Nullable InetServer inetServer, StompServerOption option) {
        return new StompServer(groups, inetServer, option);
    }

    @CanIgnoreReturnValue
    public StompServer childOption(StompClientOption option) {
        this.childOption = Assert.checkNotNull(option, "option");
        return this;
    }

    @CanIgnoreReturnValue
    public StompServer onChannel(Handler<Channel> channelHandler) {
        this.channelHandler = channelHandler;
        return this;
    }

    @CanIgnoreReturnValue
    public StompServer onStomp(Handler<StompServerHandler> stompServerHandler) {
        this.stompServerHandler = stompServerHandler;
        return this;
    }

    @CanIgnoreReturnValue
    public StompServer start() {
        StompClientOption stompClientOption = serverChildSessionOption(option, childOption);
        StompServerHandler stompServerHandler = new StompServerHandlerImpl(serverConnection);
        this.stompServerHandler.handle(stompServerHandler);

        inetServer.option(option.inetServerOption())
                .childOption(childOption.inetClientOption())
                .onChannel(channel -> {
                    if (!(channel instanceof NetChannel netChannel)) {
                        throw new IllegalArgumentException("Unsupported channel: " + channel);
                    }

                    StompClientConnection stompConnection =
                            StompClientConnection.wrap(netChannel, stompClientOption);
                    serverConnection.register(stompConnection);

                    netChannel.chain()
                            .add(new StompChannelDecoder(option.maxBodyLength(), stompConnection, stompServerHandler));

                    channelHandler.handle(netChannel);
                });

        serverConnection.bind(inetServer.channel());

        inetServer.start();

        return this;
    }

    public Promise<Void> listen(String host, int port) {
        return listen(new InetSocketAddress(host, port));
    }

    public Promise<Void> listen(InetSocketAddress address) {
        ChannelPromise channelPromise = ChannelPromise.newPromise(connection().channel());
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
        Assert.checkState(inetServer.isStart(), "inetServer is not started");
        inetServer.shutdown(timeout, unit);
    }

    public boolean isShutdown() {
        return inetServer.isShutdown();
    }

    private void listen(InetSocketAddress address, ChannelPromise resultPromise) {
        Assert.checkState(inetServer.isStart(), "inetServer is not started");

        inetServer.listen(address)
                .addListener(listenFuture -> {
                    if (listenFuture.isSuccess()) {
                        resultPromise.success();
                    } else {
                        resultPromise.fail(new StompException("Failed to listen on address " + address, listenFuture.error()));
                    }
                });
    }

    private InetServer initializeInetServer(EventLoopGroups groups) {
        return InetServer.open(groups);
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
}
