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
 * Opened file resource backed by a file channel.
 *
 * @author yun
 */
public interface FileHandle extends AutoCloseable {

    static FileHandle open(Path path) {
        FileUtils.createParentDirectories(path);
        return new LocalFileHandle(path, CREATE, READ, WRITE);
    }

    static FileHandle openReadOnly(Path path) {
        FileUtils.createParentDirectories(path);
        return new LocalFileHandle(path, READ);
    }

    static FileHandle newOpen(Path path) {
        FileUtils.createParentDirectories(path);
        return new LocalFileHandle(path, CREATE_NEW, READ, WRITE);
    }

    static FileHandle open(Path path, OpenOption... options) {
        FileUtils.createParentDirectories(path);
        return new LocalFileHandle(path, options);
    }

    Path path();

    Buffer readAll();

    Buffer read(long position, int length);

    Instant lastModified();

    long size();

    void write(Buffer source);

    void write(long position, Buffer source);

    long append(Buffer source);

    void truncate(long size);

    void force(boolean metadata);

    FileLock lock();

    @Nullable FileLock tryLock();

    @Override
    void close();
}
