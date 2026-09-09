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
 * AOF replay behavior when the file ends with a partially written record.
 *
 * @author yun
 */
public enum AofRecoveryPolicy {

    /**
     * Replay the valid prefix and stop at a truncated tail record.
     *
     * <p>This policy does not scan for the next magic value or jump over corrupted bytes. Middle
     * corruption still fails replay because AOF ordering is part of the recovered state.</p>
     */
    LOAD_TRUNCATED_TAIL,

    /**
     * Fail replay when a truncated tail record is found.
     */
    FAIL_ON_TRUNCATED_TAIL;

    /**
     * Maps external configuration values to the internal recovery policy.
     */
    public static AofRecoveryPolicy fromConfig(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return LOAD_TRUNCATED_TAIL;
        }
        return switch (value.toLowerCase(Locale.ROOT).replace('-', '_')) {
            case "load_truncated_tail" -> LOAD_TRUNCATED_TAIL;
            case "fail_on_truncated_tail" -> FAIL_ON_TRUNCATED_TAIL;
            default -> throw new IllegalArgumentException("Unknown AOF recovery policy: " + value);
        };
    }
}
