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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
import org.traffichunter.titan.core.concurrent.ScheduledPromise;
import org.traffichunter.titan.core.transport.option.InetClientOption;
import org.traffichunter.titan.core.util.Handler;
import org.traffichunter.titan.core.util.Noop;
import org.traffichunter.titan.core.util.buffer.Buffer;
import org.traffichunter.titan.core.util.channel.ChannelRegistry;

/**
 * @author yungwang-o
 */
@Slf4j
public class InetClient extends AbstractTransport<NetChannel> {

    private final AtomicReference<State> state = new AtomicReference<>(State.INIT);
    private final InetClientOption option;

    private Handler<Channel> channelHandler = channel -> {};

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
        Promise<NetChannel> connectResult = Promise.newPromise(loop);

        Promise<Void> connectRequest = loop.submit(() -> {
            try {
                channel.connect(remoteAddress, timeOut, timeUnit);
            } catch (Exception e) {
                throw new ClientException("Failed to connect to " + remoteAddress, e);
            }
        });

        connectRequest.addListener(promise -> {
            if (!promise.isSuccess()) {
                destroyChannel(channel);
                connectResult.fail(promise.error());
                return;
            }

            if (channel.isConnected()) {
                connectResult.success(channel);
                return;
            }

            ScheduledPromise<?> activeCheck = loop.scheduleAtFixedRate(() -> {
                if (connectResult.isDone()) {
                    return;
                }

                if (channel.isClosed()) {
                    destroyChannel(channel);
                    connectResult.fail(new ClientException("Channel closed before connect completed"));
                    return;
                }

                if (channel.isConnected()) {
                    connectResult.success(channel);
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
                    destroyChannel(channel);
                }
            });
        });

        return connectResult;
    }

    @Override
    public Promise<Void> send(Buffer buffer) {
        if (channels().isEmpty()) {
            log.error("Not ready to connect");
            return Promise.failedPromise(groups().secondaryGroup(), new ClientException("Not ready to connect"));
        }

        NetChannel channel = channelRegistry.selector().next();
        if (state.get() != State.STARTED || channel.isClosed() || !channel.isConnected()) {
            log.error("Not ready to connect");
            return Promise.failedPromise(groups().secondaryGroup(), new ClientException("Not ready to connect"));
        }

        IOEventLoop loop = channel.eventLoop();

        return loop.submit(() -> {
            try {
                channel.writeAndFlush(buffer);
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
    public boolean isStart() {
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
