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
package org.traffichunter.titan.core.channel.stomp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.channel.*;
import org.traffichunter.titan.core.codec.stomp.StompVersion;
import org.traffichunter.titan.core.concurrent.ChannelPromise;
import org.traffichunter.titan.core.dispatcher.Dispatcher;

/**
 * @author yungwang-o
 */
public class StompNetServerChannelImpl implements StompNetServerChannel {

    private final NetServerChannel serverChannel;
    private final StompVersion stompVersion;
    private final StompServerHandler stompServerHandler;

    StompNetServerChannelImpl(
            ChannelHandShakeEventListener channelHandShakeEventListener,
            StompVersion version
    ) {
        try {
            this.stompServerHandler = new StompServerHandler(Dispatcher.getDefault());
            this.serverChannel = NetServerChannel.open(channelHandShakeEventListener);
            this.stompVersion = version;
        } catch (IOException e) {
            throw new StompNetServeChannelException("Not open channel", e);
        }
    }

    StompNetServerChannelImpl(NetServerChannel serverChannel, StompVersion version) {
        this.serverChannel = serverChannel;
        this.stompVersion = version;
        this.stompServerHandler = new StompServerHandler(Dispatcher.getDefault());
    }

    @Override
    public ChannelHandlerChain chain() {
        return serverChannel.chain();
    }

    @Override
    public ChannelPromise register(IOEventLoop eventLoop, ChannelPromise promise) {
        return serverChannel.register(eventLoop, promise);
    }

    @Override
    public IOEventLoop eventLoop() {
        return serverChannel.eventLoop();
    }

    @Override
    public String id() {
        return serverChannel.id();
    }

    @Override
    public String session() {
        return serverChannel.session();
    }

    @Override
    public void bind(InetSocketAddress address) throws IOException {
        serverChannel.bind(address);
    }

    @Override
    public @Nullable StompNetChannel accept() {
        NetChannel channel = serverChannel.accept();
        if(channel == null) {
            return null;
        }

        return new StompNetChannelImpl(channel, stompVersion);
    }

    @Override
    public String version() {
        return stompVersion.getVersion();
    }

    @Override
    public <T> NetServerChannel setOption(SocketOption<T> option, T value) {
        return serverChannel.setOption(option, value);
    }

    @Override
    public @Nullable <T> T getOption(SocketOption<T> option) {
        return serverChannel.getOption(option);
    }

    @Override
    public Instant lastActivatedAt() {
        return serverChannel.lastActivatedAt();
    }

    @Override
    public Instant setLastActivatedAt() {
        return serverChannel.setLastActivatedAt();
    }

    @Override
    public @Nullable SocketAddress localAddress() {
        return serverChannel.localAddress();
    }

    @Override
    public @Nullable SocketAddress remoteAddress() {
        return serverChannel.remoteAddress();
    }

    @Override
    public StompHandler handler() {
        return stompServerHandler;
    }

    @Override
    public boolean isOpen() {
        return serverChannel.isOpen();
    }

    @Override
    public boolean isRegistered() {
        return serverChannel.isRegistered();
    }

    @Override
    public boolean isActive() {
        return serverChannel.isActive();
    }

    @Override
    public boolean isClosed() {
        return serverChannel.isClosed();
    }

    @Override
    public void close() {
        serverChannel.close();
    }
}
