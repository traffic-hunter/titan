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
