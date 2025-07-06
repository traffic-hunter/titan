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
package org.traffichunter.titan.core.util.channel;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.time.Instant;
import java.util.Objects;
import lombok.Getter;
import org.traffichunter.titan.core.util.IdGenerator;
import org.traffichunter.titan.core.util.concurrent.ThreadSafe;
import org.traffichunter.titan.core.util.inet.Receivable;
import org.traffichunter.titan.core.util.inet.Sendable;

/**
 * @author yungwang-o
 */
public final class ChannelContext implements Receivable, Sendable, Channel {

    private final SocketChannel socketChannel;

    @Getter
    private final Instant createdAt;

    private final String contextId;

    private ChannelContext(final SocketChannel socketChannel) {
        this.socketChannel = socketChannel;
        this.contextId = IdGenerator.uuid();
        this.createdAt = Instant.now();
    }

    @CanIgnoreReturnValue
    public static ChannelContext create(final SocketChannel socketChannel) {
        return new ChannelContext(socketChannel);
    }

    public static ChannelContext select(final SelectionKey key) {
        return (ChannelContext) key.attachment();
    }

    @Override
    public boolean isOpen() {
        return socketChannel.isOpen();
    }

    @Override
    @ThreadSafe
    public int recv(final ByteBuffer readBuf) {
        try {
            return socketChannel.read(readBuf);
        } catch (IOException e) {
            throw new IllegalStateException("Receive error = " + e.getMessage());
        }
    }

    @Override
    @ThreadSafe
    public int send(final ByteBuffer writeBuf) {
        try {
            return socketChannel.write(writeBuf);
        } catch (IOException e) {
            throw new IllegalStateException("Send error = " + e.getMessage());
        }
    }

    public boolean isClosed() {
        return !socketChannel.isOpen();
    }

    @Override
    public void close() throws IOException {
        socketChannel.close();
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
