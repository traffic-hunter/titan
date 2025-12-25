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
import org.jspecify.annotations.NullMarked;
import org.traffichunter.titan.core.concurrent.Promise;
import org.traffichunter.titan.core.util.IdGenerator;

import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.time.Instant;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

/**
 * @author yun
 */
@NullMarked
public abstract class AbstractChannel implements Channel {

    private final SelectableChannel channel;
    private final EventLoop eventLoop;
    private final ChannelChain chain = new ChannelChain();
    private volatile Instant lastActiveAt = Instant.now();
    private final String channelId = IdGenerator.uuid();

    private static final AtomicReferenceFieldUpdater<AbstractChannel, ChannelState> STATE_UPDATER =
            AtomicReferenceFieldUpdater.newUpdater(AbstractChannel.class, ChannelState.class, "state");
    private volatile ChannelState state = ChannelState.INIT;

    public AbstractChannel(EventLoop eventLoop, SelectableChannel channel) {
        this.channel = channel;
        this.eventLoop = eventLoop;

        try {
            this.channel.configureBlocking(false);
        } catch (IOException e) {
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
    public EventLoop eventLoop() {
        return eventLoop;
    }

    @Override
    public ChannelChain chain() {
        return chain;
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
        return channel.isOpen();
    }

    @Override
    public boolean isActive() {
        return channel.isOpen() && state == ChannelState.ACTIVE;
    }

    @Override
    public boolean isClosed() {
        return state.compareTo(ChannelState.CLOSED) >= 0;
    }

    @Override
    public Future<Void> close() {
        if(isClosed()) {
            return Promise.failedPromise(eventLoop, new ChannelException("Channel is already closed"));
        }

        return eventLoop.submit(() -> {
            if (!setState(ChannelState.ACTIVE, ChannelState.CLOSED)
                    && !setState(ChannelState.INIT, ChannelState.CLOSED)) {
                return;
            }
            try {
                channel.close();
            } catch (IOException e) {
                throw new ChannelException("Failed to close channel");
            }
        });
    }

    protected final boolean setState(ChannelState oldState, ChannelState newState) {
        return STATE_UPDATER.compareAndSet(this, oldState, newState);
    }

    protected final SelectableChannel getChannel() {
        return channel;
    }

    protected final ChannelState getState() {
        return state;
    }
}
