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
package org.traffichunter.titan.core.channel.websocket;

import org.traffichunter.titan.core.util.Handler;

/**
 * Handles WebSocket control frames consumed by the frame decoder.
 *
 * <p>The handler owns the received frame payload and must release it or transfer
 * its ownership before returning. The context carries the local endpoint side,
 * allowing the same stateless handler to serve client and server channels.</p>
 *
 * @author yun
 */
@FunctionalInterface
public interface WebSocketControlFrameHandler extends Handler<WebSocketContext> {

    @Override
    void handle(WebSocketContext context);
}
