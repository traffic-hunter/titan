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

import java.net.SocketAddress;
import java.net.SocketOption;
import java.time.Instant;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.util.Handler;
import org.traffichunter.titan.core.util.concurrent.ChannelPromise;

/**
 * Base abstraction for a non-blocking I/O endpoint.
 *
 * <p>A channel wraps a selectable socket-like resource and carries the state required by
 * Titan's transport layer: handler chain, event-loop ownership, identity, socket options,
 * and lifecycle state. Concrete subtypes define whether the endpoint is a listening server
 * socket or an established network connection.</p>
 *
 * <p>Registration is separate from construction. A channel can be created first, configured
 * by a transport, and later registered on the {@link IOEventLoop} that will own all selector
 * mutations and I/O callbacks for that channel.</p>
 *
 * @author yungwang-o
 */
public interface Channel {

    /**
     * Returns the inbound/outbound handler pipeline attached to this channel.
     */
    ChannelHandlerChain chain();

    /**
     * Registers this channel on an event loop using a promise created by that event loop.
     */
    default ChannelPromise register(IOEventLoop eventLoop) {
        return register(eventLoop, eventLoop.newPromise(this));
    }

    /**
     * Assigns event-loop ownership for this channel.
     */
    ChannelPromise register(IOEventLoop eventLoop, ChannelPromise promise);

    /**
     * Returns the event loop that owns this channel.
     */
    IOEventLoop eventLoop();

    /**
     * Stable channel identity used by transport registries.
     */
    String id();

    /**
     * Session identity exposed to higher-level protocols.
     */
    String session();

    @CanIgnoreReturnValue
    <T> Channel setOption(SocketOption<T> option, T value);

    @Nullable <T> T getOption(SocketOption<T> option);

    Instant lastActivatedAt();

    @CanIgnoreReturnValue
    Instant setLastActivatedAt();

    @Nullable SocketAddress localAddress();

    @Nullable SocketAddress remoteAddress();

    boolean isOpen();

    boolean isRegistered();

    boolean isActive();

    boolean isClosed();

    /**
     * Registers the handler invoked once this channel has closed, however it closed.
     */
    @CanIgnoreReturnValue
    Channel closeHandler(Handler<Channel> handler);

    void close();
}
