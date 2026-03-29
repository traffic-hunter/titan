/**
 * The MIT License
 *
 * Copyright (c) 2024 yungwang-o
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.traffichunter.titan.bootstrap;

import lombok.Getter;
import lombok.Setter;
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
@Getter
public abstract class StopWatch {

    private final Instant startTime;

    private @Nullable Instant endTime;

    protected StopWatch() {
        this.startTime = Instant.now();
    }

    public void setEndTime() {
        this.endTime = Instant.now();
    }

    public abstract Duration getUpTime();
}
