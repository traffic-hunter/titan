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

    @Override
    void close();
}
