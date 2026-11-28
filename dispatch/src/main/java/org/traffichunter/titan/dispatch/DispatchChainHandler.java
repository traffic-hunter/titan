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

/**
 * Handles one step in message dispatch.
 *
 * <p>The handler receives a mutable {@link DispatchContext} and the remaining chain.
 * Call {@link DispatchChain#next(DispatchContext)} to continue dispatch, or return
 * the supplied chain without calling it to stop.</p>
 *
 * <p>Handlers run one after another on the thread that runs the chain: the caller of
 * {@link Gateway#sparkDispatch} on the way into the queue, and the queue consumer of the
 * {@link Dispatcher} on the way out.</p>
 *
 * @author yun
 */
public interface DispatchChainHandler {

    /** Handler that passes the context on unchanged. */
    DispatchChainHandler NOOP = (context, chain) -> chain.next(context);

    /**
     * Handles one step of dispatch.
     *
     * @param context dispatch state shared by the chain
     * @param chain remaining dispatch handlers
     * @return continuation reached after this handler runs
     */
    DispatchChain handle(DispatchContext context, DispatchChain chain);
}
