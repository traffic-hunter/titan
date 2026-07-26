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
package org.traffichunter.titan.core.transport;

import java.net.InetSocketAddress;
import java.net.SocketOption;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.channel.Channel;
import org.traffichunter.titan.core.channel.ChannelHandShakeEventListener;
import org.traffichunter.titan.core.channel.EventLoopGroups;
import org.traffichunter.titan.core.channel.IOEventLoop;
import org.traffichunter.titan.core.channel.NetChannel;
import org.traffichunter.titan.core.channel.NewIONetChannel;
import org.traffichunter.titan.core.concurrent.Promise;
import org.traffichunter.titan.core.net.TlsContext;
import org.traffichunter.titan.core.net.TlsHandler;
import org.traffichunter.titan.core.net.TlsSide;
import org.traffichunter.titan.core.transport.option.InetClientOption;
import org.traffichunter.titan.core.transport.websocket.WebSocketClient;
import org.traffichunter.titan.core.util.Handler;
import org.traffichunter.titan.core.util.Noop;
import org.traffichunter.titan.core.util.Protocol;
import org.traffichunter.titan.core.util.buffer.Buffer;

/**
 * TCP client transport that can own multiple outbound channels.
 *
 * <p>The client lifecycle is intentionally separate from connection state. Each call to
 * {@link #connect(InetSocketAddress, long, TimeUnit)} creates and registers a new channel,
 * so per-connection state remains on the {@link NetChannel} itself.</p>
 *
 * @author yungwang-o
 */
@Slf4j
public class InetClient extends AbstractTransport<NetChannel> {

    private final AtomicReference<State> state = new AtomicReference<>(State.INIT);
    private final InetClientOption option;
    private Handler<Channel> channelHandler = channel -> {};

    private volatile @Nullable TlsContext tlsContext;

    private enum State {
        INIT,
        STARTED,
        SHUTDOWN
    }

    private InetClient(EventLoopGroups groups, InetClientOption option) {
        super(NewIONetChannel.class, groups);
        this.option = option;
    }

    public static InetClient open(EventLoopGroups groups) {
        return open(groups, InetClientOption.DEFAULT_INET_CLIENT_OPTION);
    }

    public static InetClient open(EventLoopGroups groups, InetClientOption option) {
        return new InetClient(groups, option);
    }

    /**
     * Registers a callback invoked for the opened channel.
     *
     * <p>The callback may run on an event-loop thread. Do not run blocking code here.</p>
     */
    @CanIgnoreReturnValue
    public InetClient onChannel(Handler<Channel> channelHandler) {
        this.channelHandler = channelHandler;
        return this;
    }

    @CanIgnoreReturnValue
    public WebSocketClient upgradeWebsocket(Protocol subProtocol) {
        return new WebSocketClient(this, subProtocol);
    }

    @CanIgnoreReturnValue
    public WebSocketClient upgradeWebsocket(Protocol subProtocol, String path) {
        return new WebSocketClient(this, subProtocol, path);
    }

    @CanIgnoreReturnValue
    public InetClient tls(TlsContext tlsContext) {
        if (isStarted()) {
            throw new IllegalStateException("TLS context already started");
        }
        if (tlsContext.options().side() != TlsSide.CLIENT) {
            throw new IllegalStateException("InetClient requires a client-side TLS context");
        }

        this.tlsContext = tlsContext;
        return this;
    }

    @Override
    public void start() {
        if (!state.compareAndSet(State.INIT, State.STARTED)) {
            throw new IllegalStateException("Cannot start client from state=" + state.get());
        }

        try {
            groups().start();
        } catch (RuntimeException e) {
            state.compareAndSet(State.STARTED, State.INIT);
            throw e;
        }
    }

    public Promise<NetChannel> connect(String host, int port) {
        return connect(host, port, 30, TimeUnit.SECONDS);
    }

    public Promise<NetChannel> connect(String host, int port, long timeout, TimeUnit unit) {
        return connect(new InetSocketAddress(host, port), timeout, unit);
    }

    public Promise<NetChannel> connect(InetSocketAddress remoteAddress, long timeOut, TimeUnit timeUnit) {
        Promise<NetChannel> validate = validateConnection();
        if (validate != null) {
            return validate;
        }

        NetChannel channel = createChannel();
        IOEventLoop loop = channel.eventLoop();

        TlsContext tlsCtx = tlsContext;
        TlsHandler tlsHandler = null;
        if (tlsCtx != null) {
            tlsHandler = tlsCtx.newHandler(remoteAddress.getHostString(), remoteAddress.getPort());
            channel.chain()
                    .addFirst(tlsHandler.inbound())
                    .addLast(tlsHandler.outbound());
        }

        Promise<NetChannel> connectResult = Promise.newPromise(loop);
        connectResult.addListener(done -> {
            if (!done.isSuccess()) {
                destroyChannel(channel);
            }
        });

        TlsHandler configuredTlsHandler = tlsHandler;
        channel.connect(remoteAddress, timeOut, timeUnit)
                .onSuccess(promise -> {
                    if (configuredTlsHandler == null) {
                        connectResult.success(channel);
                        return;
                    }

                    configuredTlsHandler.handshake(channel)
                            .onSuccess(ignored -> connectResult.success(channel))
                            .onFailure(connectResult::fail);
                }).onFailure(connectResult::fail);

        return connectResult;
    }

    @Override
    public Promise<Void> send(Buffer buffer) {
        if (channels().isEmpty()) {
            log.error("Not ready to connect");
            return Promise.failedPromise(groups().secondaryGroup(), new ClientException("Not ready to connect"));
        }

        // Outbound sends are distributed across the currently registered client channels.
        NetChannel channel = channelRegistry.selector().next();
        return send(channel, buffer);
    }

    public Promise<Void> send(NetChannel channel, Buffer buffer) {
        if (state.get() != State.STARTED || channel.isClosed() || !channel.isConnected()) {
            log.error("Not ready to connect");
            return Promise.failedPromise(groups().secondaryGroup(), new ClientException("Not ready to connect"));
        }

        Promise<Void> result = channel.writeAndFlush(buffer);
        result.onFailure(error -> log.error("Failed to send data = {}", buffer, error));
        return result;
    }

    public <C> Promise<C> failedPromise(Throwable error) {
        return Promise.failedPromise(groups().secondaryGroup(), error);
    }

    public void disconnect(NetChannel channel) {
        destroyChannel(channel);
    }

    public void shutdown() {
        shutdown(30, TimeUnit.SECONDS);
    }

    @Override
    public void shutdown(long timeOut, TimeUnit timeUnit) {
        State current = state.get();

        if (current == State.SHUTDOWN) {
            log.warn("Client is already shutdown");
            return;
        }
        if (current == State.INIT) {
            log.warn("Client is not started");
            return;
        }
        if (!state.compareAndSet(current, State.SHUTDOWN)) {
            log.warn("Client shutdown skipped. state={}", state.get());
            return;
        }

        log.info("Closing client...");
        channels().forEach(this::destroyChannel);
        groups().gracefullyShutdown(timeOut, timeUnit);
        log.info("Closed client");
    }

    @Override
    public boolean isStarted() {
        return state.get() == State.STARTED;
    }

    @Override
    public boolean isShutdown() {
        return state.get() == State.SHUTDOWN && groups().isShuttingDown();
    }

    @SuppressWarnings("unchecked")
    private void applyClientOption(NetChannel channel, InetClientOption option) {
        option.socketOptions().forEach((k, v) -> {
            channel.setOption((SocketOption<Object>) k, v);
        });
    }

    private @Nullable Promise<NetChannel> validateConnection() {
        State current = state.get();
        if (current == State.INIT) {
            return Promise.failedPromise(groups().secondaryGroup(), new ClientException("Client is not started"));
        }
        if (current == State.SHUTDOWN) {
            return Promise.failedPromise(groups().secondaryGroup(), new ClientException("Client is shutdown"));
        }
        return null;
    }

    private NetChannel createChannel() {
        NetChannel channel = newChannel(new ClientChannelConnector());
        channelHandler.handle(channel);
        applyClientOption(channel, option);
        groups().register(channel);
        return channel;
    }

    @Noop
    private static final class ClientChannelConnector implements ChannelHandShakeEventListener {

        @Override
        public void accept(Channel channel) {
        }
    }
}
