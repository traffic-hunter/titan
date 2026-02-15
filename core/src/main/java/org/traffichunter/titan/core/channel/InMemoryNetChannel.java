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
import org.traffichunter.titan.core.concurrent.ChannelPromise;
import org.traffichunter.titan.core.util.IdGenerator;
import org.traffichunter.titan.core.util.buffer.Buffer;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * @author yun
 */
public final class InMemoryNetChannel implements NetChannel {

    private final ChannelHandlerChain chain = new ChannelHandlerChain();
    private final String id = IdGenerator.uuid();
    private final Map<SocketOption<?>, Object> options = new ConcurrentHashMap<>();
    private final Queue<Buffer> inbound = new ArrayDeque<>();
    private final Queue<Buffer> pendingWrites = new ArrayDeque<>();
    private final Queue<Buffer> flushedWrites = new ArrayDeque<>();

    private @Nullable IOEventLoop eventLoop;
    private @Nullable SocketAddress localAddress;
    private @Nullable SocketAddress remoteAddress;
    private volatile Instant lastActiveAt = Instant.now();
    private volatile boolean registered;
    private volatile boolean active;
    private volatile boolean connected;
    private volatile boolean closed;

    @Override
    public ChannelHandlerChain chain() {
        return chain;
    }

    @Override
    public ChannelPromise register(IOEventLoop eventLoop, ChannelPromise promise) {
        this.eventLoop = eventLoop;
        registered = true;
        promise.success();
        return promise;
    }

    @Override
    public IOEventLoop eventLoop() {
        if (eventLoop == null) {
            throw new IllegalStateException("Event loop is not set");
        }
        return eventLoop;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String session() {
        return "test";
    }

    @Override
    public <T> NetChannel setOption(SocketOption<T> option, T value) {
        options.put(option, value);
        return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public @Nullable <T> T getOption(SocketOption<T> option) {
        return (T) options.get(option);
    }

    @Override
    public Instant lastActivatedAt() {
        return lastActiveAt;
    }

    @Override
    public Instant setLastActivatedAt() {
        lastActiveAt = Instant.now();
        return lastActiveAt;
    }

    @Override
    public @Nullable SocketAddress localAddress() {
        return localAddress;
    }

    public void setLocalAddress(SocketAddress localAddress) {
        this.localAddress = localAddress;
    }

    @Override
    public @Nullable SocketAddress remoteAddress() {
        return remoteAddress;
    }

    @Override
    public boolean isOpen() {
        return !closed;
    }

    @Override
    public boolean isRegistered() {
        return registered;
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        closed = true;
        active = false;
        connected = false;
        clearQueue(inbound);
        clearQueue(pendingWrites);
        clearQueue(flushedWrites);
    }

    @Override
    public void connect(InetSocketAddress remote, long timeOut, TimeUnit timeUnit) {
        remoteAddress = remote;
        connected = true;
        active = true;
    }

    @Override
    public void disconnect() {
        connected = false;
        active = false;
    }

    @Override
    public int read(Buffer buffer) {
        Buffer inboundBuffer = inbound.poll();
        if (inboundBuffer == null) {
            return 0;
        }
        int readable = inboundBuffer.length();
        buffer.accumulateBuffer(inboundBuffer);
        inboundBuffer.release();
        return readable;
    }

    @Override
    public void write(Buffer buffer) {
        pendingWrites.add(buffer.retain());
    }

    @Override
    public void writeAndFlush(Buffer buffer) {
        write(buffer);
        flush();
    }

    @Override
    public void flush() {
        while (!pendingWrites.isEmpty()) {
            flushedWrites.add(pendingWrites.poll());
        }
    }

    @Override
    public void onWritabilityChanged(boolean isWritable) {
    }

    @Override
    public boolean finishConnect() {
        return connected;
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    public void enqueueInbound(Buffer buffer) {
        inbound.add(buffer.retain());
    }

    public @Nullable Buffer pollWritten() {
        return flushedWrites.poll();
    }

    public int writtenCount() {
        return flushedWrites.size();
    }

    private void clearQueue(Queue<Buffer> queue) {
        while (!queue.isEmpty()) {
            queue.poll().release();
        }
    }
}
