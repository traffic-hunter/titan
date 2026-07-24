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
package org.traffichunter.titan.core.channel.websocket;

import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.channel.ChannelHandlerChain;
import org.traffichunter.titan.core.channel.IOEventLoop;
import org.traffichunter.titan.core.channel.NetChannel;
import org.traffichunter.titan.core.codec.websocket.WebSocketFrame;
import org.traffichunter.titan.core.concurrent.ChannelPromise;
import org.traffichunter.titan.core.concurrent.Promise;
import org.traffichunter.titan.core.util.Protocol;
import org.traffichunter.titan.core.util.buffer.Buffer;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * @author yun
 */
public final class WebSocketChannel implements NetChannel {

    private final NetChannel delegate;
    private final Protocol subProtocol;

    public WebSocketChannel(NetChannel delegate, Protocol subProtocol) {
        this.delegate = delegate;
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
    public Promise<Void> connect(String host, int port, long timeOut, TimeUnit timeUnit) {
        return delegate.connect(host, port, timeOut, timeUnit);
    }

    @Override
    public Promise<Void> connect(InetSocketAddress remote, long timeOut, TimeUnit timeUnit) {
        return delegate.connect(remote, timeOut, timeUnit);
    }

    @Override
    public Promise<Void> disconnect() {
        return delegate.disconnect();
    }

    @Override
    public Promise<Integer> read(Buffer buffer) {
        return delegate.read(buffer);
    }

    @Override
    public Promise<Void> write(Buffer buffer) {
        return delegate.write(buffer);
    }

    @Override
    public Promise<Void> writeAndFlush(Buffer buffer) {
        return delegate.writeAndFlush(buffer);
    }

    @Override
    public Promise<Void> flush() {
        return delegate.flush();
    }

    @Override
    public Promise<Void> onWritabilityChanged(boolean isWritable) {
        return delegate.onWritabilityChanged(isWritable);
    }

    @Override
    public Promise<Boolean> finishConnect() {
        return delegate.finishConnect();
    }

    public Promise<Void> writeAndFlush(WebSocketFrame frame) {
        if (frame.subProtocol() != subProtocol) {
            throw new IllegalArgumentException("WebSocket frame subprotocol does not match channel subprotocol");
        }

        Buffer encoded = frame.encode();
        IOEventLoop eventLoop = eventLoop();
        if (!eventLoop.inEventLoop()) {
            return eventLoop.submit(() -> writeEncoded(encoded));
        }

        Promise<Void> result = Promise.newPromise(eventLoop);
        try {
            writeEncoded(encoded);
            result.success();
        } catch (Throwable error) {
            result.fail(error);
        }
        return result;
    }

    private void writeEncoded(Buffer encoded) {
        boolean accepted = false;
        try {
            delegate.internal().write(encoded);
            accepted = true;
            delegate.internal().flush();
        } finally {
            if (!accepted) {
                encoded.release();
            }
        }
    }

    @Override
    public boolean isConnected() {
        return delegate.isConnected();
    }

    @Override
    public ChannelHandlerChain chain() {
        return delegate.chain();
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

    @Override
    public void close() {
        delegate.close();
    }
}
