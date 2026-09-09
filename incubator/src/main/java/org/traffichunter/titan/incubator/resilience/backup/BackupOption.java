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
