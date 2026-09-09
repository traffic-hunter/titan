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
 * Continuation passed to a dispatch handler.
 *
 * <p>The continuation represents only the handlers following the current
 * handler. A handler must invoke {@link #next(DispatchContext)} to propagate
 * the dispatch operation, and may omit that invocation to stop processing.</p>
 *
 * @author yun
 */
public interface DispatchChain {

    /**
     * Propagates the context to the next dispatch handler.
     *
     * @param context dispatch state shared by the chain
     * @return continuation reached after propagation
     */
    DispatchChain next(DispatchContext context);
}
