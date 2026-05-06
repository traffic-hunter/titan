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

import org.traffichunter.titan.core.concurrent.ChannelPromise;

/**
 * Event loop that owns an {@link IOSelector}.
 *
 * <p>Channels are registered with an I/O event loop before selector operations are performed.
 * The owning loop is responsible for all readiness registration and I/O callbacks for that
 * channel.</p>
 *
 * @author yungwang-o
 */
public interface IOEventLoop extends EventLoop {

    /**
     * Assigns this loop as the owner of the channel.
     */
    void register(Channel channel);

    /**
     * Returns the selector wrapper owned by this event loop.
     */
    IOSelector ioSelector();

    default ChannelPromise newPromise(Channel channel) {
        return ChannelPromise.newPromise(this, channel);
    }
}
