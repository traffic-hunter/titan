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

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.concurrent.ChannelPromise;
import org.traffichunter.titan.core.util.IdGenerator;

import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

/**
 * @author yun
 */
@Slf4j
public abstract class AbstractChannel implements Channel {

    private final SelectableChannel sc;
    private final ChannelHandShakeEventListener initializer;
    private final ChannelHandlerChain chain;
    private volatile Instant lastActiveAt = Instant.now();
    private final String channelId = IdGenerator.uuid();

    private static final AtomicReferenceFieldUpdater<AbstractChannel, ChannelState> STATE_UPDATER =
            AtomicReferenceFieldUpdater.newUpdater(AbstractChannel.class, ChannelState.class, "state");
    private volatile ChannelState state = ChannelState.INIT;

    private @Nullable volatile IOEventLoop eventLoop;
    private volatile boolean registered;

    public AbstractChannel(SelectableChannel sc, ChannelHandShakeEventListener initializer) {
        this.sc = sc;
        this.initializer = initializer;
        this.chain = new ChannelHandlerChain();

        try {
            this.sc.configureBlocking(false);
        } catch (IOException e) {
            close();
            throw new ChannelException("Failed to configure channel blocking", e);
        }
    }

    @Getter
    protected enum ChannelState {
        INIT(1),
        ACTIVE(2),
        CLOSED(3),
        ;

        private final int value;

        ChannelState(int value) {
            this.value = value;
        }
    }

    @Override
    public ChannelHandlerChain chain() {
        return chain;
    }

    @Override
    public ChannelPromise register(@NonNull IOEventLoop eventLoop, @NonNull ChannelPromise promise) {
        if(isClosed()) {
            return promise.fail(new IllegalStateException("Channel is closed"));
        }
        if(isRegistered()) {
            return promise.fail(new IllegalStateException("Channel is already registered"));
        }

        this.eventLoop = eventLoop;

        if(eventLoop.inEventLoop()) {
            registered = true;
            promise.success();
        } else {
            eventLoop.register(() -> {
                registered = true;
                promise.success();
            });
        }

        return promise;
    }

    @Override
    public @Nullable IOEventLoop eventLoop() {
        if(this.eventLoop == null) {
            throw new IllegalStateException("Event loop is not set");
        }

        return eventLoop;
    }

    @Override
    public boolean isRegistered() {
        return registered;
    }

    @Override
    public String id() {
        return channelId;
    }

    @Override
    public String session() {
        return "";
    }

    @Override
    public Instant lastActivatedAt() {
        return lastActiveAt;
    }

    @Override
    public synchronized Instant setLastActivatedAt() {
        return lastActiveAt = Instant.now();
    }

    @Override
    public boolean isOpen() {
        return sc.isOpen();
    }

    @Override
    public boolean isActive() {
        return state == ChannelState.ACTIVE;
    }

    @Override
    public boolean isClosed() {
        return state == ChannelState.CLOSED;
    }

    @Override
    public void close() {
        if(isClosed()) {
            return;
        }

        if (!setState(ChannelState.ACTIVE, ChannelState.CLOSED)
                && !setState(ChannelState.INIT, ChannelState.CLOSED)) {
            return;
        }
        try {
            sc.close();
        } catch (IOException e) {
            throw new ChannelException("Failed to close channel");
        }
    }

    void accept(Channel channel) {
        if(!setState(ChannelState.INIT, ChannelState.ACTIVE)) {
            return;
        }

        initializer.accept(channel);
    }

    protected ChannelHandShakeEventListener initializer() {
        return initializer;
    }

    protected final boolean setState(ChannelState oldState, ChannelState newState) {
        return STATE_UPDATER.compareAndSet(this, oldState, newState);
    }

    protected final SelectableChannel selectableChannel() {
        return sc;
    }

    protected final ChannelState getState() {
        return state;
    }
}
