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

import org.traffichunter.titan.core.message.Message;

/**
 * Makes sure the queue a message was routed into is being delivered.
 *
 * <p>Runs after {@link RouteDispatchChainHandler}. Delivery itself belongs to the
 * {@link Dispatcher} and is not awaited here, so dispatch completion only means the message was
 * admitted and its queue has a consumer.</p>
 *
 * @author yun
 */
final class FanoutDispatchChainHandler implements DispatchChainHandler {

    private final Dispatcher dispatcher;
    private final PathFinder pathFinder;

    FanoutDispatchChainHandler(Dispatcher dispatcher, PathFinder pathFinder) {
        this.dispatcher = dispatcher;
        this.pathFinder = pathFinder;
    }

    @Override
    public DispatchChain handle(DispatchContext context, DispatchChain chain) {
        Message message = context.getMessage();
        DispatcherQueue queue = pathFinder.get(message.getGroup(), message.getDestination());
        if (queue == null) {
            throw new IllegalStateException("Queue for " + message.getGroup() + ":" + message.getDestination()
                    + " was removed before its consumer started");
        }
        dispatcher.start(queue);
        return chain.next(context);
    }
}
