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
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.concurrent.ChannelPromise;
import org.traffichunter.titan.core.concurrent.ScheduledPromise;
import org.traffichunter.titan.core.util.buffer.Buffer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.TimeUnit;

/**
 * @author yun
 */
@Slf4j
public class NewIONetChannel extends AbstractChannel implements NetChannel {

    private final ChannelWriteBuffer channelWriteBuffer;

    private @Nullable volatile ChannelPromise connectPromise;

    public NewIONetChannel(ChannelHandShakeEventListener initializer) throws IOException {
        this(SocketChannel.open(), initializer);
    }

    NewIONetChannel(SocketChannel channel, ChannelHandShakeEventListener initializer) {
        super(channel, initializer);
        this.channelWriteBuffer = new ChannelWriteBuffer(this);
    }

    @Override
    public void connect(InetSocketAddress remoteAddress, long timeOut, TimeUnit timeUnit) throws IOException {
        if(isClosed()) {
            throw new ChannelException("Channel is closed");
        }

        connectPromise = eventLoop().newPromise(this);

        if(connect0(remoteAddress)) {
            chain().processChannelConnecting(this);
            completeConnect();
            chain().processChannelAfterConnected(this);
            eventLoop().ioSelector().registerRead(this);
            accept(this);
            return;
        }

        if(timeOut > 0) {
            ScheduledPromise<?> timeoutPromise = eventLoop().schedule(
                () -> {
                    if (connectPromise.isDone()) {
                        return;
                    }
                    connectPromise.fail(new ChannelException("Connect timeout"));
                    close();
                }, timeOut, timeUnit);

            connectPromise.addListener(f -> timeoutPromise.cancel());
        }
    }

    @Override
    public void disconnect() {
        close();
    }

    @Override
    public int read(Buffer buffer) {
        if(isClosed()) {
            throw new ChannelException("Channel is closed");
        }

        ByteBuf byteBuf = buffer.byteBuf();
        ByteBuffer dst = byteBuf.nioBuffer(byteBuf.writerIndex(), byteBuf.writableBytes());

        try {
            int read = channel().read(dst);

            if(read > 0) {
                byteBuf.writerIndex(byteBuf.writerIndex() + read);
            } else if(read < 0) {
                close();
            }

            return read;
        } catch (IOException e) {
            log.warn("Failed to read from socket. channelId={}, remoteAddress={}", id(), remoteAddress(), e);
            return -1;
        }
    }

    @Override
    public void write(Buffer buffer) {
        if(isClosed()) {
            throw new ChannelException("Already channel is closed");
        }

        channelWriteBuffer.add(buffer);
    }

    @Override
    public void writeAndFlush(Buffer buffer) {
        chain().processChannelWrite(this, buffer);
        flush();
    }

    @Override
    public void flush() {
        if(isClosed()) {
            throw new ChannelException("Already channel is closed");
        }

        while (true) {
            Buffer buffer = channelWriteBuffer.current();
            if(buffer == null) {
                break;
            }

            ByteBuf byteBuf = buffer.byteBuf();
            ByteBuffer nioBuffer = byteBuf.nioBuffer(byteBuf.readerIndex(), byteBuf.readableBytes());

            int written = write0(nioBuffer);
            if(written < 0) {
                throw new ChannelException("Failed to write to socket");
            }
            // socket buffer full
            if(written == 0) {
                onWriteabilityChanged(true);
                break;
            }

            byteBuf.readerIndex(byteBuf.readerIndex() + written);

            if(!byteBuf.isReadable()) {
                channelWriteBuffer.poll();
            }
        }

        if(channelWriteBuffer.isEmpty()) {
            onWriteabilityChanged(false);
        }
    }

    @Override
    public void onWritabilityChanged(boolean active) {
        IOEventLoop ioEventLoop = eventLoop();
        IOSelector ioSelector = ioEventLoop.ioSelector();

        ioEventLoop.register(() -> {
            try {
                if (active) {
                    ioSelector.registerWrite(this);
                } else {
                    ioSelector.unregisterWrite(this);
                }
            } catch (IOException e) {
                throw new ChannelException("Failed to register write event", e);
            }
        });
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
    public boolean finishConnect() {
        if(!eventLoop().inEventLoop()) {
            throw new ChannelException("Should be called in event loop");
        }

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

    @Override
    public void close() {
        super.close();
        if(isClosed()) {
            channelWriteBuffer.close();
        } else {
            throw new ChannelException("Channel is not closed");
        }
    }

    private void onWriteabilityChanged(boolean isWritable) {
        IOSelector ioSelector = eventLoop().ioSelector();
        try {
            if (isWritable) {
                ioSelector.registerWrite(this);
            } else {
                ioSelector.unregisterWrite(this);
            }
        } catch (IOException e) {
            throw new ChannelException("Failed to register write event", e);
        }
    }

    private SocketChannel channel() {
        return (SocketChannel) super.selectableChannel();
    }

    private int write0(ByteBuffer byteBuffer) {
        int written;
        try {
            written = channel().write(byteBuffer);
        } catch (IOException e) {
            log.warn("Failed to write to socket. channelId={}, remoteAddress={}", id(), remoteAddress(), e);
            return -1;
        }

        return written;
    }

    void completeConnect() {
        if(!eventLoop().inEventLoop()) {
            eventLoop().register(this::completeConnect);
            return;
        }

        ChannelPromise p = this.connectPromise;
        if (p != null && !p.isDone()) {
            p.success();
            connectPromise = null;
        }
    }

    private boolean connect0(InetSocketAddress remote) throws IOException {
        boolean connected = false;
        try {
            boolean connect = channel().connect(remote);
            if(!connect) {
                IOEventLoop eventLoop = eventLoop();
                eventLoop.register(() -> {
                    try {
                        eventLoop.ioSelector().registerConnect(this);
                    } catch (IOException e) {
                        throw new ChannelException("Failed to register connect event", e);
                    }
                });
            }
            connected = true;
            return connect;
        } finally {
            if(!connected) {
                close();
            }
        }
    }
}
