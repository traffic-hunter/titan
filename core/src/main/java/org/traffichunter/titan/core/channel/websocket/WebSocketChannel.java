/*
 * Copyright 2025 traffic-hunter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traffichunter.titan.core.channel.websocket;

import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.channel.Channel;
import org.traffichunter.titan.core.channel.ChannelHandlerChain;
import org.traffichunter.titan.core.channel.IOEventLoop;
import org.traffichunter.titan.core.channel.NetChannel;
import org.traffichunter.titan.core.util.Handler;
import org.traffichunter.titan.core.codec.websocket.WebSocketFrame;
import org.traffichunter.titan.core.codec.websocket.WebSocketFrames;
import org.traffichunter.titan.core.codec.websocket.WebSocketSide;
import org.traffichunter.titan.core.util.concurrent.ChannelPromise;
import org.traffichunter.titan.core.util.concurrent.ScheduledPromise;
import org.traffichunter.titan.core.util.Protocol;
import org.traffichunter.titan.core.util.buffer.Buffer;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author yun
 */
public final class WebSocketChannel implements NetChannel {

    private static final int NORMAL_CLOSURE = 1000;
    private static final long CLOSE_TIMEOUT_SECONDS = 5;

    private final NetChannel delegate;
    private final WebSocketSide side;
    private final Protocol subProtocol;
    private final AtomicBoolean closing = new AtomicBoolean();

    public WebSocketChannel(NetChannel delegate, WebSocketSide side, Protocol subProtocol) {
        this.delegate = delegate;
        this.side = side;
        this.subProtocol = subProtocol;
    }

    @Override
    public <T> NetChannel setOption(SocketOption<T> option, T value) {
        delegate.setOption(option, value);
        return this;
    }

    @Override
    public Internal internal() {
        return delegate.internal();
    }

    @Override
    public ChannelPromise connect(String host, int port, long timeOut, TimeUnit timeUnit) {
        return delegate.connect(host, port, timeOut, timeUnit);
    }

    @Override
    public ChannelPromise connect(InetSocketAddress remote, long timeOut, TimeUnit timeUnit) {
        return delegate.connect(remote, timeOut, timeUnit);
    }

    @Override
    public ChannelPromise disconnect() {
        return delegate.disconnect();
    }

    @Override
    public ChannelPromise write(Buffer buffer) {
        return delegate.write(buffer);
    }

    @Override
    public ChannelPromise writeAndFlush(Buffer buffer) {
        return delegate.writeAndFlush(buffer);
    }

    public ChannelPromise writeAndFlush(WebSocketFrame frame) {
        if (frame.subProtocol() != subProtocol) {
            throw new IllegalArgumentException("WebSocket frame subprotocol does not match channel subprotocol");
        }

        Buffer encoded = frame.encode();
        IOEventLoop eventLoop = eventLoop();
        if (!eventLoop.inEventLoop()) {
            ChannelPromise result = ChannelPromise.newPromise(eventLoop, delegate);
            try {
                eventLoop.execute(() -> completeWrite(result, encoded));
            } catch (Throwable error) {
                encoded.release();
                result.fail(error);
            }
            return result;
        }

        ChannelPromise result = ChannelPromise.newPromise(eventLoop, delegate);
        completeWrite(result, encoded);
        return result;
    }

    private void completeWrite(ChannelPromise result, Buffer encoded) {
        try {
            delegate.internal().write(encoded, result);
            delegate.internal().flush();
        } catch (Throwable error) {
            result.fail(error);
        }
    }

    @Override
    public boolean isConnected() {
        return delegate.isConnected();
    }

    @Override
    public boolean isWritable() {
        return delegate.isWritable();
    }

    @Override
    public ChannelHandlerChain chain() {
        return delegate.chain();
    }

    @Override
    public Channel closeHandler(Handler<Channel> handler) {
        return delegate.closeHandler(handler);
    }

    @Override
    public ChannelPromise register(IOEventLoop eventLoop, ChannelPromise promise) {
        return delegate.register(eventLoop, promise);
    }

    @Override
    public IOEventLoop eventLoop() {
        return delegate.eventLoop();
    }

    @Override
    public String id() {
        return delegate.id();
    }

    @Override
    public String session() {
        return delegate.session();
    }

    @Override
    public @Nullable <T> T getOption(SocketOption<T> option) {
        return delegate.getOption(option);
    }

    @Override
    public Instant lastActivatedAt() {
        return delegate.lastActivatedAt();
    }

    @Override
    public Instant setLastActivatedAt() {
        return delegate.setLastActivatedAt();
    }

    @Override
    public @Nullable SocketAddress localAddress() {
        return delegate.localAddress();
    }

    @Override
    public @Nullable SocketAddress remoteAddress() {
        return delegate.remoteAddress();
    }

    public Protocol subProtocol() {
        return subProtocol;
    }

    public NetChannel unwrap() {
        return delegate;
    }

    @Override
    public boolean isOpen() {
        return delegate.isOpen();
    }

    @Override
    public boolean isRegistered() {
        return delegate.isRegistered();
    }

    @Override
    public boolean isActive() {
        return delegate.isActive();
    }

    @Override
    public boolean isClosed() {
        return delegate.isClosed();
    }

    /**
     * Sends a normal closure frame and closes the underlying channel once it is written, or after
     * {@value #CLOSE_TIMEOUT_SECONDS} seconds if the write does not complete.
     */
    @Override
    public void close() {
        if (!delegate.isActive()) {
            delegate.close();
            return;
        }
        if (!closing.compareAndSet(false, true)) {
            return;
        }

        WebSocketFrame close = WebSocketFrames.close(NORMAL_CLOSURE, "", side, subProtocol);
        try {
            ScheduledPromise<?> timeout = eventLoop().schedule(delegate::close, CLOSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            writeAndFlush(close).addListener(result -> {
                timeout.cancel();
                delegate.close();
            });
        } catch (RuntimeException error) {
            delegate.close();
        } finally {
            close.payload().release();
        }
    }
}
