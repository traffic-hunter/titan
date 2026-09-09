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
package org.traffichunter.titan.core.util.concurrent;

/**
 * Completion callback for a {@link Promise}.
 *
 * <p>Listeners are invoked by the promise's owning event loop. Implementations should keep
 * callbacks lightweight because they run in the same execution lane as channel I/O callbacks.
 * Do not run blocking code here.</p>
 *
 * @author yungwang-o
 */
@FunctionalInterface
public interface AsyncListener<C> {

    /**
     * Handles completion of the given promise on its owning event loop.
     *
     * <p>Do not run blocking code in this callback.</p>
     */
    void onComplete(Promise<C> promise);
}
