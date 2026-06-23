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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author yun
 */
class AofBackupCoordinator implements BackupCoordinator {

    private static final Logger log = LoggerFactory.getLogger(AofBackupCoordinator.class);

    private final Path backupFilePath;
    private final BackupOption option;
    private final AppendOnlyFile aof;
    private final AtomicReference<BackupStatus> status = new AtomicReference<>(BackupStatus.INIT);

    AofBackupCoordinator(Path backupFilePath) {
        this(backupFilePath, BackupOption.defaults());
    }

    AofBackupCoordinator(Path backupFilePath, BackupOption option) {
        this.backupFilePath = backupFilePath;
        this.option = option;
        this.aof = AppendOnlyFile.open(backupFilePath, option);
        this.status.set(BackupStatus.RUNNING);
    }

    @Override
    public Path path() {
        return backupFilePath;
    }

    @Override
    public synchronized boolean inspect() {
        if (isStopped()) {
            return false;
        }
        try {
            aof.replay(metadata -> { });
            return true;
        } catch (BackupException e) {
            log.error("Failed to inspect backup file {}", backupFilePath, e);
            return false;
        }
    }

    @Override
    public synchronized boolean restore(MetadataHandler handler) {
        if (isStopped()) {
            return false;
        }
        try {
            aof.replay(handler);
            return true;
        } catch (BackupException e) {
            log.error("Failed to restore backup file {}", backupFilePath, e);
            return false;
        }
    }

    @Override
    public synchronized void record(Metadata metadata) {
        if (isStopped()) {
            throw new BackupException("Backup coordinator is stopped");
        }
        aof.append(metadata);
    }

    @Override
    public boolean isRunning() {
        return status.get() == BackupStatus.RUNNING;
    }

    @Override
    public boolean isStopped() {
        return status.get() == BackupStatus.STOPPED;
    }

    @Override
    public BackupStatus status() {
        return status.get();
    }

    @Override
    public BackupOption options() {
        return option;
    }

    @Override
    public synchronized void close() {
        if (status.getAndSet(BackupStatus.STOPPED) != BackupStatus.STOPPED) {
            aof.close();
        }
    }
}
