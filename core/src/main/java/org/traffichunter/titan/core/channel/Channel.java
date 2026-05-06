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

import java.net.SocketAddress;
import java.net.SocketOption;
import java.time.Instant;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.concurrent.ChannelPromise;

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

    void close();
}
