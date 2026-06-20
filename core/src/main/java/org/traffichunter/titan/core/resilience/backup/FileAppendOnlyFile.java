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
