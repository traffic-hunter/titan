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
package org.traffichunter.titan.core.channel;

import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.util.concurrent.Promise;

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

    private final Internal internal = new NewIOInternal();

    NewIONetServerChannel(ChannelHandShakeEventListener initializer) throws IOException {
        this(ServerSocketChannel.open(), initializer);
    }

    NewIONetServerChannel(ServerSocketChannel channel, ChannelHandShakeEventListener initializer) {
        super(channel, initializer);
    }

    @Override
    public Internal internal() {
        return internal;
    }

    @Override
    public Promise<Void> bind(String host, int port) {
        return bind(new InetSocketAddress(host, port));
    }

    @Override
    public Promise<Void> bind(InetSocketAddress address) {
        return ChannelTasks.bind(this, address);
    }

    @Override
    public Promise<NetChannel> accept() {
        return ChannelTasks.accept(this);
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

    private final class NewIOInternal implements Internal {

        @Override
        public void bind(InetSocketAddress address) throws IOException {
            channel().bind(address);
        }

        @Override
        public @Nullable NetChannel accept() {
            try {
                SocketChannel accepted = channel().accept();
                if (accepted == null) {
                    return null;
                }

                return new NewIONetChannel(accepted, NewIONetServerChannel.super.initializer());
            } catch (IOException e) {
                setState(getState(), ChannelState.INIT);
                return null;
            }
        }
    }
}
