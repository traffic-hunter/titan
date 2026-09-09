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
import java.util.function.LongSupplier;
import org.traffichunter.titan.core.util.buffer.Buffer;
import org.traffichunter.titan.core.util.file.FileHandle;

/**
 * File-backed {@link AppendOnlyFile} implementation.
 *
 * <p>The implementation writes encoded records at the file tail and replays from byte offset zero.
 * It is intentionally simple for v1: no rewrite, no manifest, and no scan-forward repair. Those
 * belong to a later rewrite/snapshot layer.</p>
 *
 * @author yun
 */
public final class FileAppendOnlyFile implements AppendOnlyFile {

    private static final long EVERY_SEC_MILLIS = 1000;

    private final FileHandle fileHandle;
    private final AofSyncPolicy syncPolicy;
    private final AofRecoveryPolicy recoveryPolicy;
    private final LongSupplier clock;
    private long lastSyncMillis;

    /**
     * Opens a file-backed append-only log using the given durability and recovery policies.
     */
    public FileAppendOnlyFile(Path path, AofSyncPolicy syncPolicy, AofRecoveryPolicy recoveryPolicy) {
        this(path, new BackupOption(BackupType.AOF, syncPolicy, recoveryPolicy));
    }

    /**
     * Opens a file-backed append-only log using explicit backup options.
     */
    public FileAppendOnlyFile(Path path, BackupOption option) {
        this(FileHandle.open(path), option, System::currentTimeMillis);
    }

    /**
     * Creates a file-backed append-only log with injected file and clock dependencies for
     * deterministic tests.
     */
    FileAppendOnlyFile(
            FileHandle fileHandle,
            BackupOption option,
            LongSupplier clock
    ) {
        this.fileHandle = fileHandle;
        this.syncPolicy = option.syncPolicy();
        this.recoveryPolicy = option.recoveryPolicy();
        this.clock = clock;
        this.lastSyncMillis = clock.getAsLong();
    }

    @Override
    public long append(Metadata metadata) {
        Buffer encoded = MetadataCodec.encode(metadata);
        try {
            long offset = fileHandle.append(encoded);
            syncIfNeeded();
            return offset;
        } finally {
            // The encoded buffer is created by this log. Source metadata payload ownership stays
            // with the caller, but the transient encoded representation is released here.
            encoded.release();
        }
    }

    @Override
    public void replay(MetadataHandler handler) {
        Buffer content = fileHandle.readAll();
        try {
            byte[] bytes = content.getBytes();
            int offset = 0;
            while (offset < bytes.length) {
                try {
                    DecodedMetadata decoded = MetadataCodec.decode(bytes, offset);
                    handler.handle(decoded.metadata());
                    offset += decoded.length();
                } catch (TruncatedBackupRecordException e) {
                    // Tolerant loading only accepts a partial record at the file tail.
                    // We stop at the valid prefix instead of scanning forward for another magic.
                    if (recoveryPolicy == AofRecoveryPolicy.LOAD_TRUNCATED_TAIL) {
                        return;
                    }
                    throw e;
                }
            }
        } finally {
            content.release();
        }
    }

    @Override
    public void fsync() {
        fileHandle.force(true);
        lastSyncMillis = clock.getAsLong();
    }

    @Override
    public Path path() {
        return fileHandle.path();
    }

    @Override
    public void truncate(long size) {
        fileHandle.truncate(size);
    }

    @Override
    public void close() {
        fileHandle.close();
    }

    private void syncIfNeeded() {
        switch (syncPolicy) {
            case EVERY -> fsync();
            case EVERY_SEC -> {
                long now = clock.getAsLong();
                if (now - lastSyncMillis >= EVERY_SEC_MILLIS) {
                    fsync();
                }
            }
            case NO -> {
                // Depend on the OS unless fsync() is called explicitly.
            }
        }
    }
}
