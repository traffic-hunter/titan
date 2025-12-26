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

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

/**
 * @author yun
 */
public class NewIONetServerChannel extends AbstractChannel implements NetServerChannel {

    private volatile ChannelInitializer initializer;

    public NewIONetServerChannel() throws IOException {
        this(ServerSocketChannel.open());
    }

    NewIONetServerChannel(ServerSocketChannel channel) {
        super(channel);
    }

    @Override
    public void init(ChannelInitializer initializer) {
        this.initializer = initializer;
    }

    ChannelInitializer initializer() {
        return initializer;
    }

    @Override
    public void bind(@NonNull InetSocketAddress address) {
        try {
            channel().bind(address);
            setState(getState(), ChannelState.ACTIVE);
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

            return new NewIONetChannel(accept);
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public <T> NetServerChannel setOption(@NonNull SocketOption<T> option, @NonNull T value) {
        try {
            channel().setOption(option, value);
        } catch (IOException e) {
            throw new ChannelException("Failed to set socket option = " + option.name() + " value = " + value, e);
        }
        return this;
    }

    @Override
    public @Nullable <T> T getOption(@NonNull SocketOption<T> option) {
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
