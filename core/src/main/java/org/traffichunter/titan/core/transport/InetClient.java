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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketOption;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.channel.Channel;
import org.traffichunter.titan.core.channel.ChannelHandShakeEventListener;
import org.traffichunter.titan.core.channel.EventLoopGroups;
import org.traffichunter.titan.core.channel.IOEventLoop;
import org.traffichunter.titan.core.channel.NetChannel;
import org.traffichunter.titan.core.concurrent.Promise;
import org.traffichunter.titan.core.concurrent.ScheduledPromise;
import org.traffichunter.titan.core.transport.option.InetClientOption;
import org.traffichunter.titan.core.util.Handler;
import org.traffichunter.titan.core.util.Noop;
import org.traffichunter.titan.core.util.buffer.Buffer;

/**
 * @author yungwang-o
 */
@Slf4j
public class InetClient extends AbstractTransport<NetChannel> {

    private final AtomicReference<State> state = new AtomicReference<>(State.INIT);
    private final InetClientOption option;

    private volatile Handler<Channel> channelHandler = channel -> {};

    private enum State {
        INIT,
        STARTED,
        CONNECTING,
        CONNECTED,
        SHUTDOWN
    }

    private InetClient(NetChannel channel, EventLoopGroups groups, InetClientOption option) {
        super(channel, groups);
        this.option = option;
    }

    public static InetClient open(EventLoopGroups groups) {
        return open(groups, InetClientOption.DEFAULT_INET_CLIENT_OPTION);
    }

    public static InetClient open(EventLoopGroups groups, InetClientOption option) {
        ClientChannelConnector connector = new ClientChannelConnector();

        try {
            NetChannel channel = NetChannel.open(connector);
            groups.register(channel);
            return new InetClient(channel, groups, option);
        } catch (IOException e) {
            throw new ClientException("Failed to create client", e);
        }
    }

    @CanIgnoreReturnValue
    public InetClient onChannel(Handler<Channel> channelHandler) {
        this.channelHandler = channelHandler;
        return this;
    }

    @Override
    public void start() {
        if (!state.compareAndSet(State.INIT, State.STARTED)) {
            throw new IllegalStateException("Cannot start client from state=" + state.get());
        }

        try {
            channelHandler.handle(channel());
            applyClientOption(option);
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

        IOEventLoop loop = channel().eventLoop();
        Promise<NetChannel> connectResult = Promise.newPromise(loop);

        Promise<Void> connectRequest = loop.submit(() -> {
            try {
                channel().connect(remoteAddress, timeOut, timeUnit);
            } catch (IOException e) {
                throw new ClientException("Failed to connect to " + remoteAddress, e);
            }
        });

        connectRequest.addListener(promise -> {
            if (!promise.isSuccess()) {
                state.compareAndSet(State.CONNECTING, State.STARTED);
                connectResult.fail(promise.error());
                return;
            }

            if (channel().isConnected()) {
                state.set(State.CONNECTED);
                connectResult.success(channel());
                return;
            }

            ScheduledPromise<?> activeCheck = loop.scheduleAtFixedRate(() -> {
                if (connectResult.isDone()) {
                    return;
                }

                if (channel().isClosed()) {
                    state.compareAndSet(State.CONNECTING, State.STARTED);
                    connectResult.fail(new ClientException("Channel closed before connect completed"));
                    return;
                }

                if (channel().isConnected()) {
                    state.set(State.CONNECTED);
                    connectResult.success(channel());
                }
            }, 1, 10, TimeUnit.MILLISECONDS);

            ScheduledPromise<?> timeoutCheck = loop.schedule(() -> {
                if (!connectResult.isDone()) {
                    connectResult.fail(new ClientException("Connect completion timeout"));
                }
            }, timeOut, timeUnit);

            connectResult.addListener(done -> {
                activeCheck.cancel();
                timeoutCheck.cancel();
                if (!done.isSuccess()) {
                    state.compareAndSet(State.CONNECTING, State.STARTED);
                }
            });
        });

        return connectResult;
    }

    @Override
    public Promise<Void> send(Buffer buffer) {
        if (state.get() != State.CONNECTED || channel().isClosed() || !channel().isConnected()) {
            log.error("Not ready to connect");
            return Promise.failedPromise(groups().secondaryGroup(), new ClientException("Not ready to connect"));
        }

        IOEventLoop loop = channel().eventLoop();

        return loop.submit(() -> {
            try {
                channel().writeAndFlush(buffer);
            } catch (Exception e) {
                log.error("Failed to send data = {}", buffer, e);
                throw new ClientException("Failed to send data", e);
            }
        });
    }

    public void shutdown() {
        shutdown(30, TimeUnit.SECONDS);
    }

    @Override
    public void shutdown(long timeOut, TimeUnit timeUnit) {
        do {
            State current = state.get();
            if (current == State.SHUTDOWN) {
                log.warn("Client is already shutdown");
                return;
            }
            if (current == State.INIT) {
                log.warn("Client is not started");
                return;
            }
            if (state.compareAndSet(current, State.SHUTDOWN)) {
                break;
            }
        } while (true);

        if (channel().isClosed()) {
            log.info("Client channel is already closed");
            groups().gracefullyShutdown(timeOut, timeUnit);
            return;
        }

        log.info("Closing client...");
        close(timeOut, timeUnit);
        log.info("Closed client");
    }

    @Override
    public boolean isStart() {
        State current = state.get();
        return current == State.STARTED || current == State.CONNECTING || current == State.CONNECTED;
    }

    @Override
    public boolean isShutdown() {
        return state.get() == State.SHUTDOWN && super.isShutdown();
    }

    @SuppressWarnings("unchecked")
    private void applyClientOption(InetClientOption option) {
        option.socketOptions().forEach((k, v) -> {
            channel().setOption((SocketOption<Object>) k, v);
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
        if (current == State.CONNECTING || current == State.CONNECTED || channel().isConnected()) {
            return Promise.failedPromise(groups().secondaryGroup(), new ClientException("Client is already connecting or connected"));
        }
        if (!state.compareAndSet(State.STARTED, State.CONNECTING)) {
            return Promise.failedPromise(groups().secondaryGroup(), new ClientException("Cannot connect from state=" + state.get()));
        }
        if (channel().isClosed()) {
            state.compareAndSet(State.CONNECTING, State.STARTED);
            return Promise.failedPromise(groups().secondaryGroup(), new ClientException("Channel is closed"));
        }
        return null;
    }

    @Noop
    private static final class ClientChannelConnector implements ChannelHandShakeEventListener {

        @Override
        public void accept(Channel channel) {
        }
    }
}
