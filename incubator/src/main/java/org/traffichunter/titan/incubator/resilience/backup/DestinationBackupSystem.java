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

import org.traffichunter.titan.core.util.file.FileHandler;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author yun
 */
public final class DestinationBackupSystem implements AutoCloseable {

    private final Path backupDirectory;
    private final BackupOption options;
    private final Map<String, BackupCoordinator> coordinators = new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    public DestinationBackupSystem() {
        this(BackupOption.defaults());
    }

    public DestinationBackupSystem(BackupOption options) {
        this(Path.of("."), options);
    }

    public DestinationBackupSystem(Path backupDirectory, BackupOption options) {
        this.backupDirectory = backupDirectory;
        this.options = options;
    }

    public void record(Metadata metadata) {
        if (closed.get()) {
            throw new BackupException("Backup system is stopped");
        }
        coordinator(metadata.destinationPath()).record(metadata);
    }

    public boolean inspect(String destination) {
        if (closed.get()) {
            return false;
        }
        return coordinator(destination).inspect();
    }

    public boolean restore(String destination, MetadataHandler handler) {
        if (closed.get()) {
            return false;
        }
        return coordinator(destination).restore(handler);
    }

    @Override
    public void close() throws Exception {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        Exception failure = null;
        for (BackupCoordinator coordinator : coordinators.values()) {
            try {
                coordinator.close();
            } catch (Exception e) {
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            }
        }
        coordinators.clear();

        if (failure != null) {
            throw failure;
        }
    }

    private BackupCoordinator coordinator(String destination) {
        return coordinators.computeIfAbsent(destination, this::newAofCoordinator);
    }

    private BackupCoordinator newAofCoordinator(String destination) {
        Path path = FileHandler.resolveDestinationFile(backupDirectory, destination, true);
        return new AofBackupCoordinator(path, options);
    }
}
