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
package org.traffichunter.titan.core.util.file;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;

import java.nio.channels.FileLock;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.util.buffer.Buffer;

/**
 * High-level handle for a single opened file.
 *
 * <p>The handle exposes Titan {@link Buffer} based read and write operations instead of leaking
 * {@code FileChannel} position management to callers. Methods that receive a {@code Buffer} never
 * release it. Methods that return a {@code Buffer} allocate a new buffer and transfer release
 * ownership to the caller.</p>
 *
 * @author yun
 */
public interface FileHandle extends AutoCloseable {

    static FileHandle open(Path path) {
        FileHandler.createParentDirectories(path);
        return new LocalFileHandle(path, CREATE, READ, WRITE);
    }

    static FileHandle openReadOnly(Path path) {
        FileHandler.createParentDirectories(path);
        return new LocalFileHandle(path, READ);
    }

    static FileHandle newOpen(Path path) {
        FileHandler.createParentDirectories(path);
        return new LocalFileHandle(path, CREATE_NEW, READ, WRITE);
    }

    static FileHandle open(Path path, OpenOption... options) {
        FileHandler.createParentDirectories(path);
        return new LocalFileHandle(path, options);
    }

    Path path();

    /**
     * Reads the whole file into a newly allocated buffer.
     *
     * @return buffer owned by the caller
     */
    Buffer readAll();

    /**
     * Reads up to {@code length} bytes from {@code position} into a newly allocated buffer.
     *
     * @return buffer owned by the caller
     */
    Buffer read(long position, int length);

    Instant lastModified();

    long size();

    /**
     * Writes the readable bytes of {@code source} at the handle's current file position.
     *
     * <p>The source buffer remains owned by the caller and is not released.</p>
     */
    void write(Buffer source);

    /**
     * Writes the readable bytes of {@code source} starting at {@code position}.
     *
     * <p>The source buffer remains owned by the caller and is not released.</p>
     */
    void write(long position, Buffer source);

    /**
     * Appends the readable bytes of {@code source} to the end of the file.
     *
     * <p>The source buffer remains owned by the caller and is not released.</p>
     *
     * @return start offset where the bytes were appended
     */
    long append(Buffer source);

    void truncate(long size);

    void force(boolean metadata);

    FileLock lock();

    @Nullable FileLock tryLock();

    @Override
    void close();
}
