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
