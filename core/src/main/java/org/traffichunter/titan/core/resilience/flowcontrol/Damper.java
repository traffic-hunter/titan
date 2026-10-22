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
package org.traffichunter.titan.core.resilience.flowcontrol;

/**
 * Controls whether work may pass through a flow-control boundary.
 *
 * <p>A damper is open while work may continue and closed while pressure requires admission to
 * stop. Implementations decide which resource to observe and use {@link #regulate()} to refresh
 * their state.
 *
 * @author yun
 */
public interface Damper {

    /**
     * Re-evaluates the observed resource and returns the resulting state.
     *
     * @return the state after regulation
     */
    DamperStatus regulate();

    /** Opens the damper explicitly. */
    void open();

    /** Closes the damper explicitly. */
    void close();

    /**
     * Returns the current state without performing a new resource check.
     *
     * @return the current damper state
     */
    DamperStatus getStatus();

    /**
     * Returns whether work may currently pass through this damper.
     *
     * @return {@code true} when the current state is open
     */
    default boolean isOpen() {
        return getStatus() == DamperStatus.OPEN;
    }

    /**
     * Returns whether this damper is currently applying flow control.
     *
     * @return {@code true} when the current state is closed
     */
    default boolean isClosed() {
        return getStatus() == DamperStatus.CLOSED;
    }
}
