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
 * Backup strategy selected by external configuration.
 *
 * @author yun
 */
public enum BackupType {

    /**
     * Append-only log backup.
     */
    AOF;

    /**
     * Maps external configuration values to a backup type.
     */
    public static BackupType fromConfig(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return AOF;
        }
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "aof" -> AOF;
            default -> throw new IllegalArgumentException("Unknown backup type: " + value);
        };
    }

    public boolean isAof() {
        return this == AOF;
    }
}
