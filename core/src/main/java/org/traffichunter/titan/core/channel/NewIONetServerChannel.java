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
package org.traffichunter.titan.core.channel;

import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

/**
 * {@link NetServerChannel} implementation backed by Java NIO
 * {@link ServerSocketChannel}.
 *
 * <p>This channel represents the listening socket owned by the server primary
 * I/O event loop. It accepts inbound TCP connections and wraps each accepted
 * {@link SocketChannel} as a {@link NewIONetChannel}. The accepted child channel
 * receives the same handshake initializer so the server transport can run its
 * channel setup consistently after the child is assigned to a secondary I/O
 * event loop.</p>
 *
 * <p>{@link #accept()} is non-blocking when the underlying
 * {@code ServerSocketChannel} is configured that way by the transport. A
 * {@code null} return value means the selector reported an accept-ready event
 * but no additional connection is currently available.</p>
 */
public class NewIONetServerChannel extends AbstractChannel implements NetServerChannel {

    NewIONetServerChannel(ChannelHandShakeEventListener initializer) throws IOException {
        this(ServerSocketChannel.open(), initializer);
    }

    NewIONetServerChannel(ServerSocketChannel channel, ChannelHandShakeEventListener initializer) {
        super(channel, initializer);
    }

    @Override
    public void bind(InetSocketAddress address) {
        try {
            channel().bind(address);
        } catch (IOException e) {
            setState(getState(), ChannelState.INIT);
        }
    }

    @Override
    public @Nullable NetChannel accept() {
        try {
            SocketChannel accept = channel().accept();
            if (accept == null) {
                return null;
            }

            return new NewIONetChannel(accept, super.initializer());
        } catch (IOException e) {
            setState(getState(), ChannelState.INIT);
            return null;
        }
    }

    @Override
    public <T> NetServerChannel setOption(SocketOption<T> option, T value) {
        try {
            channel().setOption(option, value);
        } catch (IOException e) {
            throw new ChannelException("Failed to set socket option = " + option.name() + " value = " + value, e);
        }
        return this;
    }

    @Override
    public @Nullable <T> T getOption(SocketOption<T> option) {
        try {
            return channel().getOption(option);
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public @Nullable SocketAddress localAddress() {
        try {
            return channel().getLocalAddress();
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public SocketAddress remoteAddress() {
        throw new UnsupportedOperationException("Not supported!!");
    }

    private ServerSocketChannel channel() {
        return (ServerSocketChannel) super.selectableChannel();
    }
}
