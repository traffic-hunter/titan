/*
 * Copyright 2024 traffic-hunter
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
package org.traffichunter.titan.bootstrap;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Atomic one-way start guard for the bootstrap lifecycle.
 *
 * <p>The bootstrap path should only transition from "not started" to "started"
 * once per instance. {@link #start()} returns whether the caller won that
 * transition, which keeps duplicate startup attempts explicit and cheap.</p>
 */
public final class BootState {

    private static final Boolean STATE_NONE = false;
    private static final Boolean STATE_STARTED = true;

    private final AtomicBoolean state = new AtomicBoolean(STATE_NONE);

    boolean getState() {
        return state.get();
    }

    public boolean start() {
        return state.compareAndSet(STATE_NONE, STATE_STARTED);
    }
}
