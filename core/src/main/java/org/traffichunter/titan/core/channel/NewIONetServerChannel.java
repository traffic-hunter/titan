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

/**
 * @author yun
 */
public class NewIONetServerChannel extends AbstractChannel implements NetServerChannel {

    public NewIONetServerChannel(EventLoop eventLoop) throws IOException {
        this(eventLoop, ServerSocketChannel.open());
    }

    NewIONetServerChannel(EventLoop eventLoop, ServerSocketChannel channel) {
        super(eventLoop, channel);
    }

    @Override
    public void bind(String host, int port) throws IOException {
        channel().bind(new InetSocketAddress(host, port));
        setState(getState(), ChannelState.ACTIVE);
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
        return (ServerSocketChannel) super.getChannel();
    }
}
