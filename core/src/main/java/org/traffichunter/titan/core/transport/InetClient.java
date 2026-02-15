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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.channel.*;
import org.traffichunter.titan.core.concurrent.Promise;
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

    public Promise<Void> connect(String host, int port) {
        return connect(new InetSocketAddress(host, port));
    }

    public Promise<Void> connect(InetSocketAddress remoteAddress) {
        if(channel().isClosed()) {
            log.error("Already connected or closed, cannot connect");
            return Promise.failedPromise(groups().secondaryGroup(), new ClientException("Already connected or closed, cannot connect"));
        }

        IOEventLoop loop = channel().eventLoop();

        return loop.submit(() -> {
            try {
                channel().connect(remoteAddress, 5, TimeUnit.SECONDS);
            } catch (IOException e) {
                throw new ClientException("Failed to connect to " + remoteAddress, e);
            }
        });
    }

    public Promise<Void> send(Buffer buffer) {
        if(channel().isClosed()) {
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

    public static class Builder {

        private @Nullable EventLoopGroups groups;
        private @Nullable Handler<Channel> channelHandler;
        private final Map<SocketOption<?>, Object> options = new HashMap<>();

        public Builder group(EventLoopGroups groups) {
            this.groups = groups;
            return this;
        }

        public Builder channelHandler(Handler<Channel> channelHandler) {
            this.channelHandler = channelHandler;
            return this;
        }

        public Builder option(SocketOption<?> option, Object value) {
            options.put(option, value);
            return this;
        }

        public InetClient build() {
            try {
                NetChannel channel = NetChannel.open(new ClientChannelConnector());
                groups.register(channel);
                return new InetClient(channel, groups);
            } catch (IOException e) {
                throw new ClientException("Failed to create client", e);
            }
        }
    }

    private static class ClientChannelConnector implements ChannelHandShakeEventListener {

        @Override
        public void accept(Channel channel) {
        }
    }
}