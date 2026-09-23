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
import org.traffichunter.titan.core.util.Handler;
import org.traffichunter.titan.core.util.concurrent.ChannelPromise;
import org.traffichunter.titan.core.util.IdGenerator;
import org.traffichunter.titan.core.util.buffer.Buffer;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * @author yun
 */
public final class InMemoryNetChannel implements NetChannel {

    private final ChannelHandlerChain chain = new ChannelHandlerChain();
    private final String id = IdGenerator.uuid();
    private final Map<SocketOption<?>, Object> options = new ConcurrentHashMap<>();
    private final Queue<Buffer> inbound = new ArrayDeque<>();
    private final Queue<PendingWrite> pendingWrites = new ArrayDeque<>();
    private final Queue<Buffer> flushedWrites = new ArrayDeque<>();
    private final Internal internal = new InMemoryInternal();

    private @Nullable IOEventLoop eventLoop;
    private @Nullable SocketAddress localAddress;
    private @Nullable SocketAddress remoteAddress;
    private volatile Instant lastActiveAt = Instant.now();
    private volatile boolean registered;
    private volatile boolean active;
    private volatile boolean connected;
    private volatile boolean closed;
    private volatile Handler<Channel> closeHandler = ignored -> {};

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
    public Channel closeHandler(Handler<Channel> handler) {
        this.closeHandler = handler;
        return this;
    }

    @Override
    public void close() {
        closed = true;
        active = false;
        connected = false;
        clearQueue(inbound);
        failPendingWrites();
        clearQueue(flushedWrites);
        closeHandlerChain();
        closeHandler.handle(this);
    }

    private void closeHandlerChain() {
        IOEventLoop owner = eventLoop;
        if (!registered || owner == null || owner.inEventLoop()) {
            chain.close();
            return;
        }

        try {
            owner.execute(chain::close);
        } catch (RejectedExecutionException e) {
            chain.close();
        }
    }

    @Override
    public Internal internal() {
        return internal;
    }

    @Override
    public ChannelPromise connect(String host, int port, long timeOut, TimeUnit timeUnit) {
        return connect(new InetSocketAddress(host, port), timeOut, timeUnit);
    }

    @Override
    public ChannelPromise connect(InetSocketAddress remote, long timeOut, TimeUnit timeUnit) {
        return ChannelIO.execute(this, () -> {
            chain.processChannelConnecting(this);
            remoteAddress = remote;
            connected = true;
            active = true;
            chain.processChannelAfterConnected(this);
        });
    }

    @Override
    public ChannelPromise disconnect() {
        return ChannelIO.disconnect(this);
    }

    @Override
    public ChannelPromise write(Buffer buffer) {
        return ChannelIO.write(this, buffer);
    }

    @Override
    public ChannelPromise writeAndFlush(Buffer buffer) {
        return ChannelIO.writeAndFlush(this, buffer);
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public boolean isWritable() {
        return !closed;
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

    private void failPendingWrites() {
        PendingWrite pending;
        while ((pending = pendingWrites.poll()) != null) {
            pending.buffer.release();
            if (pending.promise != null) {
                pending.promise.fail(new ChannelWriteException(
                        ChannelWriteException.Reason.NOT_SENT, "Channel closed before the write started"));
            }
        }
    }

    private record PendingWrite(Buffer buffer, @Nullable ChannelPromise promise) {
    }

    private final class InMemoryInternal implements Internal {

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
        public void write(Buffer buffer, ChannelPromise promise) {
            pendingWrites.add(new PendingWrite(buffer.retain(), promise));
        }

        @Override
        public void write(List<Buffer> buffers, ChannelPromise promise) {
            for (int i = 0; i < buffers.size(); i++) {
                boolean last = i == buffers.size() - 1;
                pendingWrites.add(new PendingWrite(buffers.get(i).retain(), last ? promise : null));
            }
            if (buffers.isEmpty()) {
                promise.success();
            }
        }

        @Override
        public void writeAndFlush(Buffer buffer, ChannelPromise promise) {
            write(buffer, promise);
            flush();
        }

        @Override
        public void flush() {
            PendingWrite pending;
            while ((pending = pendingWrites.poll()) != null) {
                flushedWrites.add(pending.buffer);
                if (pending.promise != null) {
                    pending.promise.success();
                }
            }
        }

        @Override
        public void onWritabilityChanged(boolean isWritable) {
        }

        @Override
        public boolean finishConnect() {
            return connected;
        }
    }
}
