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
