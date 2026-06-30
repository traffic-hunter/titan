/*
The MIT License

Copyright (c) 2025 traffic-hunter

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
*/
package org.traffichunter.titan.incubator.resilience.backup;

import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * Sync policy for Titan append-only backup writes.
 *
 * <p>Configuration strings are mapped through {@link #fromConfig(String)}.</p>
 *
 * @author yun
 */
public enum AofSyncPolicy {

    /**
     * Force the AOF file after every append.
     */
    EVERY,

    /**
     * Force the AOF file at most once per second during append.
     */
    EVERY_SEC,

    /**
     * Do not force during append; leave flushing to the operating system unless {@code fsync()} is
     * called explicitly.
     */
    NO;

    /**
     * Maps external configuration values to the internal policy names.
     *
     * @param value configuration value such as {@code every}, {@code every-sec}, {@code every_sec},
     *              or {@code no}
     * @return matching AOF sync policy
     */
    public static AofSyncPolicy fromConfig(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return EVERY_SEC;
        }
        return switch (value.toLowerCase(Locale.ROOT).replace('-', '_')) {
            case "every" -> EVERY;
            case "every_sec" -> EVERY_SEC;
            case "no" -> NO;
            default -> throw new IllegalArgumentException("Unknown AOF sync policy: " + value);
        };
    }
}
