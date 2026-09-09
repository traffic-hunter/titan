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
package org.traffichunter.titan.dispatch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traffichunter.titan.core.message.Message;
import org.traffichunter.titan.core.util.Destination;

/**
 * Routes an inbound message into memory before later fanout handlers run.
 *
 * @author yun
 */
final class RouteDispatchChainHandler implements DispatchChainHandler {

    private static final Logger log = LoggerFactory.getLogger(RouteDispatchChainHandler.class);

    private final Dispatcher dispatcher;

    RouteDispatchChainHandler(Dispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Override
    public DispatchChain handle(DispatchContext context, DispatchChain chain) {
        Message message = context.getMessage();
        Destination destination = message.getDestination();

        DispatcherQueue dq = dispatcher.getOrPut(destination);

        if (dq.enqueue(message) == null) {
            log.warn("Dispatcher queue is full, no message was enqueued = {}", destination);
            throw new IllegalStateException("Dispatcher queue is full = " + destination);
        }

        return chain.next(context);
    }
}
