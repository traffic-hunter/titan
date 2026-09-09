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
package org.traffichunter.titan.core.channel.stomp;

import org.traffichunter.titan.core.util.Handler;

/**
 * @author yun
 */
public interface StompServerHandler extends StompHandler {

    StompServerHandler receivedFrameHandler(Handler<StompServerEvent> handler);

    StompServerHandler connectHandler(StompServerCommandHandler handler);

    StompServerHandler disconnectHandler(StompServerCommandHandler handler);

    StompServerHandler subscribeHandler(StompServerCommandHandler handler);

    StompServerHandler unsubscribeHandler(StompServerCommandHandler handler);

    StompServerHandler sendHandler(StompServerCommandHandler handler);

    StompServerHandler ackHandler(StompServerCommandHandler handler);

    StompServerHandler nackHandler(StompServerCommandHandler handler);

    StompServerHandler beginHandler(StompServerCommandHandler handler);

    StompServerHandler abortHandler(StompServerCommandHandler handler);

    StompServerHandler commitHandler(StompServerCommandHandler handler);

    StompServerHandler pingHandler(StompServerCommandHandler handler);
}
