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
