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
 * <p>Handlers run sequentially on the dispatch executor selected by the gateway.</p>
 *
 * @author yun
 */
public interface DispatchChainHandler {

    /** Sentinel behavior that completes without modifying the dispatch context. */
    DispatchChainHandler NOOP = (context, chain) -> chain.next(context);

    /**
     * Processes one dispatch stage.
     *
     * @param context state shared by the dispatch lifecycle
     * @param chain remaining dispatch handlers
     * @return continuation reached after this handler runs
     */
    DispatchChain handle(DispatchContext context, DispatchChain chain);
}
