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

import io.netty.buffer.ByteBuf;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.concurrent.EventLoop;
import org.traffichunter.titan.core.concurrent.Promise;
import org.traffichunter.titan.core.util.buffer.Buffer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.SocketChannel;

/**
 * @author yun
 */
@Slf4j
public class NewIONetChannel extends AbstractChannel implements NetChannel {

    private final ChannelOutBoundBuffer outBoundBuffer;

    public NewIONetChannel(EventLoop eventLoop) throws IOException {
        this(eventLoop, SocketChannel.open());
    }

    NewIONetChannel(EventLoop eventLoop, SocketChannel channel) {
        super(eventLoop, channel);
        this.outBoundBuffer = new ChannelOutBoundBuffer();
    }

    @Override
    public Promise<Void> connect(InetSocketAddress remote) {
        return eventLoop().submit(() -> {
            try {
                channel().connect(remote);
            } catch (IOException e) {
                throw new ChannelException("Failed to connect to " + remote, e);
            }
        });
    }

    @Override
    public Promise<Void> disconnect() {
        return null;
    }

    @Override
    public Promise<Void> read(Buffer buffer) {
        return eventLoop().submit(() -> {
            if(isClosed()) {
                throw new ChannelException("Already channel is closed");
            }

            ByteBuf byteBuf = buffer.byteBuf();
            ByteBuffer dst = byteBuf.nioBuffer(byteBuf.writerIndex(), byteBuf.writableBytes());

            try {
                int read = channel().read(dst);
                if(read > 0) {
                    byteBuf.writerIndex(byteBuf.writerIndex() + read);
                } else if(read < 0) {
                    throw new ChannelException("Failed to read from socket");
                }

            } catch (IOException e) {
                log.error("Error reading from socket = {}", e.getMessage());
                close();
            }
        });
    }

    @Override
    public Promise<Void> write(Buffer buffer) {
        return null;
    }

    @Override
    public Promise<Void> writeAndFlush(Buffer buffer) {
        return null;
    }

    @Override
    public Promise<Void> flush() {
        return null;
    }

    @Override
    public <T> NetChannel setOption(SocketOption<T> option, T value) {
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
    public @Nullable SocketAddress remoteAddress() {
        try {
            return channel().getRemoteAddress();
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public boolean isFinishConnected() {
        try {
            return channel().finishConnect();
        } catch (IOException e) {
            log.warn("Failed to finish connect = {}", e.getMessage());
            return false;
        }
    }

    @Override
    public boolean isConnected() {
        return channel().isConnected();
    }

    private SocketChannel channel() {
        return (SocketChannel) super.getChannel();
    }
}
