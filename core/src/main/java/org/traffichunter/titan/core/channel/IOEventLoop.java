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

import org.traffichunter.titan.core.util.concurrent.ChannelPromise;

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
