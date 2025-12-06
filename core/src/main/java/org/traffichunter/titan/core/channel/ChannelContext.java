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
package org.traffichunter.titan.core.channel;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.netty.buffer.ByteBuf;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.traffichunter.titan.core.util.IdGenerator;
import org.traffichunter.titan.core.util.buffer.Buffer;

/**
 * @author yungwang-o
 */
@Slf4j
public class ChannelContext implements Context {

    private final SocketChannel channel;
    private final Instant createdAt;
    private final String contextId;
    private final AtomicBoolean isClosed =  new AtomicBoolean(false);

    protected ChannelContext(final SocketChannel channel) {
        this(channel, IdGenerator.uuid());
    }

    protected ChannelContext(final SocketChannel channel, final String contextId) {
        this(channel, contextId, Instant.now());
    }

    private ChannelContext(final SocketChannel channel, final String contextId, final Instant createdAt) {
        Objects.requireNonNull(channel, "socketChannel");
        Objects.requireNonNull(contextId, "contextId");
        Objects.requireNonNull(createdAt, "createdAt");

        this.channel = channel;
        this.contextId = contextId;
        this.createdAt = createdAt;
    }

    public static ChannelContext create(final SocketChannel socketChannel) {
        return new ChannelContext(socketChannel);
    }

    public static ChannelContext select(final SelectionKey key) {
        return (ChannelContext) key.attachment();
    }

    @Override
    public boolean isOpen() {
        return channel.isOpen();
    }

    @Override
    @CanIgnoreReturnValue
    public int recv(final Buffer buffer) {
        if(isClosed()) {
            return -1;
        }

        try {
            ByteBuf byteBuf = buffer.byteBuf();
            ByteBuffer dst = byteBuf.nioBuffer(byteBuf.writerIndex(), byteBuf.writableBytes());

            int read = channel.read(dst);
            if(read > 0) {
                byteBuf.writerIndex(byteBuf.writerIndex() + read);
            } else if(read < 0) {
                close();
            }

            return read;
        } catch (IOException e) {
            log.error("Error reading from socket = {}", e.getMessage());
            return -1;
        }
    }

    @Override
    @CanIgnoreReturnValue
    public int write(final Buffer buffer) {
        if(isClosed()) {
            return -1;
        }

        try {
            ByteBuf byteBuf = buffer.byteBuf();
            ByteBuffer dst = byteBuf.nioBuffer(byteBuf.readerIndex(), byteBuf.readableBytes());

            int write = channel.write(dst);
            if(write > 0) {
                byteBuf.readerIndex(byteBuf.readerIndex() + write);
            } else if(write < 0) {
                close();
            }

            return write;
        } catch (IOException e) {
            log.info("Error writing to socket = {}", e.getMessage());
            return -1;
        }
    }

    public SocketChannel socketChannel() {
        return channel;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public String contextId() { return contextId; }

    public boolean isClosed() {
        return isClosed.get() || !channel.isOpen();
    }

    @Override
    public void close() throws IOException {
        if(isClosed.compareAndSet(false, true)) {
            channel.close();
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ChannelContext that)) {
            return false;
        }
        return Objects.equals(contextId, that.contextId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(contextId);
    }
}
