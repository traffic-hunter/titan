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
import java.util.function.Consumer;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.channel.*;
import org.traffichunter.titan.core.concurrent.ChannelPromise;
import org.traffichunter.titan.core.concurrent.Promise;
import org.traffichunter.titan.core.concurrent.ScheduledPromise;
import org.traffichunter.titan.core.transport.option.InetClientOption;
import org.traffichunter.titan.core.util.Assert;
import org.traffichunter.titan.core.util.Handler;
import org.traffichunter.titan.core.util.buffer.Buffer;

/**
 * @author yungwang-o
 */
@Slf4j
public class InetClient extends AbstractTransport<NetChannel> {

    private InetClient(NetChannel channel, EventLoopGroups groups) {
        super(channel, groups);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public void start() {
        groups().start();
    }

    public Promise<NetChannel> connect(String host, int port) {
        return connect(host, port, 30, TimeUnit.SECONDS);
    }

    public Promise<NetChannel> connect(String host, int port, long timeout, TimeUnit unit) {
        return connect(new InetSocketAddress(host, port), timeout, unit);
    }

    public Promise<NetChannel> connect(InetSocketAddress remoteAddress, long timeOut, TimeUnit timeUnit) {
        if(channel().isClosed()) {
            log.error("Already connected or closed, cannot connect");
            return Promise.failedPromise(groups().secondaryGroup(), new ClientException("Already connected or closed, cannot connect"));
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
                connectResult.fail(promise.error());
                return;
            }

            if (channel().isConnected()) {
                connectResult.success(channel());
                return;
            }

            ScheduledPromise<?> activeCheck = loop.scheduleAtFixedRate(() -> {
                if (connectResult.isDone()) {
                    return;
                }

                if (channel().isClosed()) {
                    connectResult.fail(new ClientException("Channel closed before connect completed"));
                    return;
                }

                if (channel().isConnected()) {
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
            });
        });

        return connectResult;
    }

    @Override
    public Promise<Void> send(Buffer buffer) {
        if(channel().isClosed() || !channel().isConnected()) {
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
        if(channel().isClosed()) {
            log.warn("Failed to close client, already closed or not connected");
            return;
        }

        log.info("Closing client...");

        close(timeOut, timeUnit);

        log.info("Closed client");
    }

    @SuppressWarnings("unchecked")
    public static class Builder {

        private @Nullable EventLoopGroups groups;
        private @Nullable Handler<Channel> channelHandler;
        private Consumer<NetChannel> optionApplier = channel -> { };

        @CanIgnoreReturnValue
        public Builder group(EventLoopGroups groups) {
            this.groups = groups;
            return this;
        }

        @CanIgnoreReturnValue
        public Builder channelHandler(Handler<Channel> channelHandler) {
            this.channelHandler = channelHandler;
            return this;
        }

        @CanIgnoreReturnValue
        public Builder option(Consumer<NetChannel> optionApplier) {
            this.optionApplier = this.optionApplier.andThen(optionApplier);
            return this;
        }

        @CanIgnoreReturnValue
        @SuppressWarnings("unchecked")
        public Builder options(InetClientOption option) {
            optionApplier = optionApplier.andThen(channel ->
                    option.socketOptions().forEach((k, v) ->
                            channel.setOption((SocketOption<Object>) k, v)
                    )
            );
            return this;
        }

        public InetClient build() {
            Assert.checkNotNull(groups, "groups cannot be null");
            Assert.checkNotNull(channelHandler, "channelHandler cannot be null");

            try {
                NetChannel channel = NetChannel.open(new ClientChannelConnector(channelHandler));
                optionApplier.accept(channel);
                groups.register(channel);
                return new InetClient(channel, groups);
            } catch (IOException e) {
                throw new ClientException("Failed to create client", e);
            }
        }
    }

    private static class ClientChannelConnector implements ChannelHandShakeEventListener {

        private final Handler<Channel> channelHandler;

        private ClientChannelConnector(Handler<Channel> channelHandler) {
            this.channelHandler = channelHandler;
        }

        @Override
        public void accept(Channel channel) {
            channelHandler.handle(channel);
        }
    }
}
