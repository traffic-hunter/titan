/*
 * Copyright 2024 yungwang-o
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

import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;

/**
 * <p>
 * Abstract base class for measuring elapsed time.
 * </p>
 * <p>
 * This class captures the start time upon instantiation and allows subclasses
 * to define how the end time is set and how uptime (duration between start and end) is calculated.
 * </p>
 * <p>Usage:</p>
 * <pre>{@code
 * public class TaskStopWatch extends StopWatch {
 *
 *     @Override
 *     public Duration getUpTime() {
 *         return Duration.between(getStartTime(), getEndTime());
 *     }
 * }
 *
 * TaskStopWatch task = new TaskStopWatch();
 * System.out.println("Uptime: " + task.getUpTime().toSeconds() + " seconds");
 * }</pre>
 *
 * @see Instant
 * @see Duration
 *
 * @author yungwang-o
*/
public abstract class StopWatch {

    private final Instant startTime;

    private @Nullable Instant endTime;

    protected StopWatch() {
        this.startTime = Instant.now();
    }

    public Instant getStartTime() {
        return startTime;
    }

    public @Nullable Instant getEndTime() {
        return endTime;
    }

    public void setEndTime() {
        this.endTime = Instant.now();
    }

    public abstract Duration getUpTime();
}
