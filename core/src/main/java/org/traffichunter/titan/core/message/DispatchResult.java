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
package org.traffichunter.titan.core.message;

import org.jspecify.annotations.Nullable;

/**
 * Outcome of an attempt to admit a message to a destination queue.
 *
 * <p>Only {@link #ACCEPTED} means the message entered the queue. Every other result explains
 * why admission was refused. Acceptance does not guarantee delivery to any consumer.</p>
 *
 * <p>Code {@code 0} denotes acceptance. Queue rejection codes start at {@code 1000};
 * their meanings must remain stable once exposed to clients.</p>
 *
 * @author yun
 */
public enum DispatchResult {

    /**
     * The message was admitted to the queue.
     */
    ACCEPTED(0),

    /**
     * The queue has insufficient pending-byte capacity, or admission remains suspended
     * until pressure falls below the resume threshold.
     */
    FULL(1000),

    /**
     * The queue is manually paused and refuses new messages until it is resumed.
     */
    PAUSED(1001),

    /**
     * The message alone exceeds the queue's pending-byte limit and cannot fit even
     * when the queue is empty.
     */
    TOO_LARGE(1002),

    /**
     * The queue is closed and no longer accepts messages.
     */
    CLOSED(1003);

    private final int code;

    DispatchResult(int code) {
        this.code = code;
    }

    /**
     * Returns the numeric result code: {@code 0} for acceptance, or a queue rejection code.
     *
     * @return the stable numeric code for this result
     */
    public int code() {
        return code;
    }

    /**
     * Looks up a result by its numeric code without treating unknown codes as refusals.
     *
     * @param code numeric result code
     * @return the matching result, or {@code null} when the code is unknown
     */
    public static @Nullable DispatchResult fromCode(int code) {
        for (DispatchResult result : values()) {
            if (result.code == code) {
                return result;
            }
        }
        return null;
    }
}
