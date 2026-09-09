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
 * Handles both inbound and outbound events for a channel.
 *
 * <p>A duplex handler occupies one logical position in the channel pipeline. Inbound events
 * flow from the transport toward the application, while outbound events flow from the
 * application toward the transport. Both directions can share state, as TLS requires.</p>
 *
 * <p>Handlers run on the channel event loop. Implementing both directions does not make a handler
 * thread-safe or make its callbacks run concurrently.</p>
 *
 * @author yun
 */
public interface ChannelDuplexHandler extends ChannelInBoundHandler, ChannelOutBoundHandler {
}
