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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullUnmarked;
import org.traffichunter.titan.core.util.concurrent.ChannelPromise;
import org.traffichunter.titan.core.util.Handler;
import org.traffichunter.titan.core.util.IdGenerator;

import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.time.Instant;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

/**
 * Common implementation for selectable channels.
 *
 * <p>The class owns state that is shared by server and network channels: the underlying
 * {@link SelectableChannel}, lifecycle transitions, handler pipeline, event-loop ownership,
 * and generated identifiers. Concrete implementations provide socket-specific operations
 * such as connect, accept, read, and write.</p>
 *
 * <p>The state machine is intentionally small. A channel starts in {@code INIT}, becomes
 * {@code ACTIVE} when its handshake listener runs, and finally moves to {@code CLOSED}.
 * The handshake listener is the extension point used by transports to attach protocol
 * handlers and register accepted channels.</p>
 *
 * @author yun
 */
@NullUnmarked
public abstract class AbstractChannel implements Channel {

    private static final Logger log = LoggerFactory.getLogger(AbstractChannel.class);

    private final SelectableChannel sc;
    private final ChannelHandShakeEventListener initializer;
    private final ChannelHandlerChain chain;
    private volatile Instant lastActiveAt = Instant.now();
    private final String channelId = IdGenerator.randomId16("channel");
    private final String sessionId = IdGenerator.randomId16("session");

    private static final AtomicReferenceFieldUpdater<AbstractChannel, ChannelState> STATE_UPDATER =
            AtomicReferenceFieldUpdater.newUpdater(AbstractChannel.class, ChannelState.class, "state");
    private volatile ChannelState state = ChannelState.INIT;

    private volatile IOEventLoop eventLoop;
    private volatile boolean registered;
    private volatile Handler<@NonNull Channel> closeHandler = ignored -> {};

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

    protected enum ChannelState {
        INIT(1),
        ACTIVE(2),
        CLOSED(3),
        ;

        private final int value;

        ChannelState(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
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
            eventLoop.execute(() -> {
                registered = true;
                promise.success();
            });
        }

        return promise;
    }

    @Override
    public IOEventLoop eventLoop() {
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
        return sessionId;
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
    public Channel closeHandler(@NonNull Handler<@NonNull Channel> handler) {
        this.closeHandler = handler;
        return this;
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
        } finally {
            closeHandlerChain();
            try {
                closeHandler.handle(this);
            } catch (Exception error) {
                log.warn("Failed to notify the channel close handler. channelId={}", channelId, error);
            }
        }
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
            // The owner no longer executes channel work, so direct cleanup cannot race decoding.
            chain.close();
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
