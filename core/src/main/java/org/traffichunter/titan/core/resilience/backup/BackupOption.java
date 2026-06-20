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

import org.jspecify.annotations.Nullable;

/**
 * Options used when opening an append-only backup file.
 *
 * <p>The record keeps durability and recovery policy together so later backup settings can be
 * added without expanding constructor parameter lists.</p>
 *
 * @author yun
 */
public record BackupOption(
        BackupType type,
        AofSyncPolicy syncPolicy,
        AofRecoveryPolicy recoveryPolicy
) {

    /**
     * Returns the default backup option.
     */
    public static BackupOption defaults() {
        return new BackupOption(
                BackupType.AOF,
                AofSyncPolicy.EVERY_SEC,
                AofRecoveryPolicy.LOAD_TRUNCATED_TAIL
        );
    }

    /**
     * Creates backup options from external configuration values.
     */
    public static BackupOption fromConfig(
            @Nullable String type,
            @Nullable String syncPolicy,
            @Nullable String recoveryPolicy
    ) {
        return new BackupOption(
                BackupType.fromConfig(type),
                AofSyncPolicy.fromConfig(syncPolicy),
                AofRecoveryPolicy.fromConfig(recoveryPolicy)
        );
    }

    /**
     * Creates append-log backup options from external configuration values.
     */
    public static BackupOption fromConfig(@Nullable String syncPolicy, @Nullable String recoveryPolicy) {
        return fromConfig("aof", syncPolicy, recoveryPolicy);
    }
}
