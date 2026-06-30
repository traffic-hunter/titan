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
