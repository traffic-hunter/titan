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

/**
 * Listener invoked when a channel completes or reports a handshake step.
 *
 * <p>The listener receives the {@link Channel} that produced the event, allowing
 * handlers to inspect channel state or continue any handshake-dependent setup.</p>
 *
 * @author yun
 */
@FunctionalInterface
public interface ChannelHandShakeEventListener {

    /**
     * Handles the handshake event for the given channel.
     *
     * <p>Implementations should keep this callback lightweight because it may be
     * invoked from the channel's event-loop thread. Do not run blocking code here.</p>
     */
    void accept(Channel channel);
}
