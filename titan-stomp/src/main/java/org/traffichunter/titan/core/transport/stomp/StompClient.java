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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.channel.Channel;
import org.traffichunter.titan.core.channel.EventLoopGroups;
import org.traffichunter.titan.core.channel.NetChannel;
import org.traffichunter.titan.core.channel.stomp.StompClientConnection;
import org.traffichunter.titan.core.channel.stomp.StompClientHandler;
import org.traffichunter.titan.core.channel.stomp.StompNetChannelException;
import org.traffichunter.titan.core.codec.stomp.*;
import org.traffichunter.titan.core.concurrent.Promise;
import org.traffichunter.titan.core.concurrent.ScheduledPromise;
import org.traffichunter.titan.core.transport.InetClient;
import org.traffichunter.titan.core.transport.stomp.option.StompClientOption;
import org.traffichunter.titan.core.util.Handler;

import static org.traffichunter.titan.core.codec.stomp.StompFrame.HeartBeat;
import static org.traffichunter.titan.core.codec.stomp.StompHeaders.Elements;

/**
 * @author yun
 */
@Slf4j
public final class StompClient {

    private final InetClient inetClient;
    private final StompClientOption option;

    private Handler<StompClientHandler> stompClientHandler = handler -> {};
    private @Nullable StompClientConnection connection;

    private StompClient(EventLoopGroups groups, @Nullable InetClient inetClient, StompClientOption option) {
        this.option = option;

        if (inetClient == null) {
            inetClient = InetClient.open(groups, option.inetClientOption());
        }
        this.inetClient = inetClient;
    }

    public static StompClient open(EventLoopGroups groups, StompClientOption option) {
        return open(groups, null, option);
    }

    public static StompClient open(EventLoopGroups groups, @Nullable InetClient inetClient, StompClientOption option) {
        return new StompClient(groups, inetClient, option);
    }

    @CanIgnoreReturnValue
    public StompClient onChannel(Handler<Channel> channelHandler) {
        inetClient.onChannel(channelHandler);
        return this;
    }

    @CanIgnoreReturnValue
    public StompClient onStomp(Handler<StompClientHandler> stompClientHandler) {
        this.stompClientHandler = stompClientHandler;
        return this;
    }

    public void start() {
        if(inetClient.isStart()) {
            throw new StompException("Client already started");
        }

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
                            .thenCompose(frame -> awaitConnected(conn, remoteAddress, timeOut, timeUnit));
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

    public void handler(Consumer<StompClientConnection> connection) {
        connection.accept(connection());
    }

    private StompClientConnection createConnection(NetChannel channel) {
        StompClientConnection connection = StompClientConnection.wrap(channel, option);
        stompClientHandler.handle(connection.handler());
        channel.chain().add(new StompChannelDecoder(option.maxFrameLength(), connection, connection.handler()));
        this.connection = connection;
        return connection;
    }

    private StompFrame generateConnectFrame(String host) {
        StompHeaders headers = StompHeaders.create();
        headers.put(Elements.ACCEPT_VERSION, option.stompVersion().getVersion());

        if (!option.bypassHostHeader()) {
            headers.put(Elements.HOST, host);
        }
        if (option.virtualHost() != null) {
            headers.put(Elements.HOST, option.virtualHost());
        }
        if (option.login() != null) {
            headers.put(Elements.LOGIN, option.login());
        }
        if (option.passcode() != null) {
            headers.put(Elements.PASSCODE, option.passcode());
        }

        headers.put(Elements.HEART_BEAT, HeartBeat.create(option.heartbeatX(), option.heartbeatY()).value());
        StompCommand command = option.useStompFrame() ? StompCommand.STOMP : StompCommand.CONNECT;
        return StompFrame.create(headers, command);
    }

    private Promise<StompClientConnection> awaitConnected(
            StompClientConnection conn,
            InetSocketAddress remoteAddress,
            long timeOut,
            TimeUnit timeUnit
    ) {
        Promise<StompClientConnection> result = Promise.newPromise(conn.channel().eventLoop());
        ScheduledPromise<Object> timeoutTask = conn.channel().eventLoop().schedule(() -> {
            if (result.tryFail(new StompNetChannelException("Timed out waiting for CONNECTED from " + remoteAddress + " in " + timeOut + " " + timeUnit))) {
                conn.close();
            }
        }, timeOut, timeUnit);

        conn.connectedPromise().addListener(connectedFuture -> {
            timeoutTask.cancel();
            if (connectedFuture.isSuccess()) {
                result.trySuccess(conn);
                return;
            }

            Throwable error = connectedFuture.error();
            if (error != null) {
                result.tryFail(error);
            } else {
                result.tryFail(new StompNetChannelException("STOMP connect failed without error cause"));
            }
        });

        return result;
    }
}
