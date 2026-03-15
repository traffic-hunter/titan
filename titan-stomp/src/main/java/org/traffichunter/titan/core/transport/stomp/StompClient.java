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
import java.net.SocketAddress;
import java.net.SocketOption;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.channel.Channel;
import org.traffichunter.titan.core.channel.EventLoopGroups;
import org.traffichunter.titan.core.channel.NetChannel;
import org.traffichunter.titan.core.channel.stomp.StompClientConnection;
import org.traffichunter.titan.core.codec.stomp.*;
import org.traffichunter.titan.core.concurrent.Promise;
import org.traffichunter.titan.core.transport.InetClient;
import org.traffichunter.titan.core.transport.option.InetClientOption;
import org.traffichunter.titan.core.transport.stomp.option.StompClientOption;
import org.traffichunter.titan.core.util.Assert;
import org.traffichunter.titan.core.util.Handler;

import static org.traffichunter.titan.core.codec.stomp.StompFrame.*;
import static org.traffichunter.titan.core.codec.stomp.StompHeaders.*;

/**
 * @author yun
 */
@Slf4j
public final class StompClient {

    private final InetClient inetClient;
    private final StompClientOption option;
    private @Nullable StompClientConnection connection;

    private StompClient(
            InetClient inetClient,
            StompClientOption option
    ) {
        this.inetClient = inetClient;
        this.option = option;
    }

    public static Builder builder() {
        return new Builder();
    }

    public void start() {
        inetClient.start();
    }

    public Promise<StompClientConnection> connect(String host, int port) {
        return connect(host, port, 30, TimeUnit.SECONDS);
    }

    public Promise<StompClientConnection> connect(String host, int port, long timeOut, TimeUnit timeUnit) {
        return connect(new InetSocketAddress(host, port), timeOut, timeUnit);
    }

    public Promise<StompClientConnection> connect(InetSocketAddress remoteAddress, long timeOut, TimeUnit timeUnit) {
        return inetClient.connect(remoteAddress, timeOut, timeUnit)
                .map(this::createConnection)
                .thenCompose(conn -> {
                    StompFrame connectFrame = generateConnectFrame(remoteAddress.getHostString());
                    return conn.send(connectFrame)
                            .map(frame -> conn);
                }).onFailure(error -> log.error("Failed to connect to {}", remoteAddress, error));
    }

    public void shutdown() {
        shutdown(30, TimeUnit.SECONDS);
    }

    public void shutdown(long timeout, TimeUnit unit) {
        inetClient.shutdown(timeout, unit);
    }

    public @Nullable SocketAddress remoteAddress() {
        return inetClient.remoteAddress();
    }

    public boolean isStart() {
        return inetClient.isStart();
    }

    public boolean isClosed() {
        return inetClient.isClosed();
    }

    public String version() {
        return StompVersion.STOMP_1_2.getVersion();
    }

    public StompClientOption option() {
        return option;
    }

    public StompClientConnection connection() {
        StompClientConnection connection = this.connection;
        if (connection == null) {
            throw new IllegalStateException("STOMP client is not connected");
        }
        return connection;
    }

    private StompClientConnection createConnection(NetChannel channel) {
        StompClientConnection connection = StompClientConnection.wrap(channel, option);
        channel.chain().add(new StompChannelDecoder(option.maxFrameLength(), connection, connection.handler()));
        this.connection = connection;
        return connection;
    }

    private StompFrame generateConnectFrame(String host) {
        StompHeaders headers = create();
        String accepted = option.stompVersion().getVersion();
        headers.put(Elements.ACCEPT_VERSION, accepted);

        if(!option.bypassHostHeader()) {
            headers.put(Elements.HOST, host);
        }
        if(option.virtualHost() != null) {
            headers.put(Elements.HOST, option.virtualHost());
        }
        if(option.login() != null) {
            headers.put(Elements.LOGIN, option.login());
        }
        if(option.passcode() != null) {
            headers.put(Elements.PASSCODE, option.passcode());
        }

        headers.put(Elements.HEART_BEAT, HeartBeat.create(option.heartbeatX(), option.heartbeatY()).value());

        StompCommand command = option.useStompFrame() ? StompCommand.STOMP : StompCommand.CONNECT;

        return StompFrame.create(headers, command);
    }

    public static final class Builder {

        private @Nullable EventLoopGroups groups;
        private StompClientOption option = StompClientOption.DEFAULT_STOMP_CLIENT_OPTION;
        private Consumer<NetChannel> optionApplier = channel -> { };
        private Handler<Channel> channelHandler = channel -> { };

        @CanIgnoreReturnValue
        public Builder group(EventLoopGroups groups) {
            this.groups = groups;
            return this;
        }

        @CanIgnoreReturnValue
        public Builder option(StompClientOption option) {
            this.option = option;
            if (option.inetClientOption() != null) {
                option(option.inetClientOption());
            }
            return this;
        }

        @CanIgnoreReturnValue
        @SuppressWarnings("unchecked")
        public Builder option(InetClientOption option) {
            this.optionApplier = this.optionApplier.andThen(channel ->
                    option.socketOptions().forEach((k, v) ->
                            channel.setOption((SocketOption<Object>) k, v)
                    ));
            return this;
        }

        @CanIgnoreReturnValue
        public Builder channelHandler(Handler<Channel> channelHandler) {
            this.channelHandler = channelHandler;
            return this;
        }

        public StompClient build() {
            Assert.checkNotNull(groups, "groups cannot be null");

            InetClient inetClient = InetClient.builder()
                    .group(groups)
                    .channelHandler(channelHandler)
                    .option(optionApplier)
                    .build();

            return new StompClient(inetClient, option);
        }
    }
}
