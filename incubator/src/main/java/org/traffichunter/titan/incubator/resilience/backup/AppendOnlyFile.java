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

import java.nio.file.Path;

/**
 * Append-only backup file for sequential durability records.
 *
 * <p>The file stores Titan backup metadata records sequentially and replays only the order encoded
 * in the log. Implementations must not mutate or release buffers owned by the source
 * {@link Metadata}.</p>
 *
 * @author yun
 */
public interface AppendOnlyFile extends AutoCloseable {

    /**
     * Opens an append-only file with default sync and recovery policies.
     */
    static AppendOnlyFile open(Path path) {
        return open(path, BackupOption.defaults());
    }

    /**
     * Opens an append-only file with explicit backup options.
     */
    static AppendOnlyFile open(Path path, BackupOption option) {
        return new FileAppendOnlyFile(path, option);
    }

    /**
     * Opens an append-only file with explicit sync and recovery policies.
     */
    static AppendOnlyFile open(Path path, AofSyncPolicy syncPolicy, AofRecoveryPolicy recoveryPolicy) {
        return open(path, new BackupOption(BackupType.AOF, syncPolicy, recoveryPolicy));
    }

    /**
     * Appends one encoded metadata record and returns the starting file offset.
     */
    long append(Metadata metadata);

    /**
     * Replays valid records from the beginning of the file.
     *
     * <p>Truncated tail records follow the configured recovery policy. Invalid or corrupted
     * records fail replay immediately.</p>
     */
    void replay(MetadataHandler handler);

    /**
     * Forces pending file writes to durable storage.
     */
    void fsync();

    /**
     * Returns the underlying file path.
     */
    Path path();

    void truncate(long size);

    @Override
    void close();
}
