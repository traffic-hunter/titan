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

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import org.traffichunter.titan.core.util.Handler;

/**
 * Takes messages out of destination queues and delivers them to their subscribers.
 *
 * @author yun
 */
public interface Dispatcher extends AutoCloseable {

    /**
     * Sets the handlers every message goes through after it leaves its queue and before it is
     * delivered. A message a handler does not pass on is dropped.
     *
     * @param chainHandler callback that adds the handlers to the chain
     * @return this dispatcher
     */
    @CanIgnoreReturnValue
    Dispatcher chainHandler(Handler<DispatchHandlerChain> chainHandler);

    /** Starts delivering from the queue. Does nothing when the queue is already being delivered. */
    void start(DispatcherQueue queue);

    /** Stops delivering from the queue. Messages still in it stay there. */
    void stop(DispatcherQueue queue);

    /** Stops delivering from every queue. */
    @Override
    void close();
}
