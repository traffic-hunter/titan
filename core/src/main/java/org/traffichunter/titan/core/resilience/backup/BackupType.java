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
package org.traffichunter.titan.core.resilience.backup;

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
