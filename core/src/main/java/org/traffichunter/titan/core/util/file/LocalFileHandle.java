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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.util.Assert;
import org.traffichunter.titan.core.util.buffer.Buffer;
import org.traffichunter.titan.core.util.buffer.Buffers;

/**
 * Local {@link FileHandle} implementation backed by {@link FileChannel}.
 *
 * @author yun
 */
final class LocalFileHandle implements FileHandle {

    private final Path path;
    private final FileChannel fileChannel;

    LocalFileHandle(Path path, OpenOption... options) {
        this.path = path;
        try {
            this.fileChannel = FileChannel.open(path, options);
        } catch (IOException e) {
            throw new FileIOException("Failed to open file channel: " + path, e);
        }
    }

    @Override
    public Path path() {
        return path;
    }

    @Override
    public Buffer readAll() {
        long size = size();
        if (size > Integer.MAX_VALUE) {
            throw new FileIOException("File is too large to read into a Buffer: " + path);
        }
        return read(0, (int) size);
    }

    @Override
    public Buffer read(long position, int length) {
        Assert.checkArgument(position >= 0, "position must be greater than or equal to zero");
        Assert.checkArgument(length >= 0, "length must be greater than or equal to zero");

        Buffer destination = Buffer.alloc(length);
        try {
            ByteBuffer buffer = Buffers.writableByteBuffer(destination);
            long nextPosition = position;
            while (buffer.hasRemaining()) {
                int read = fileChannel.read(buffer, nextPosition);
                if (read < 0) {
                    break;
                }
                Buffers.updateWriterIndex(destination, read);
                nextPosition += read;
            }
            return destination;
        } catch (IOException e) {
            destination.release();
            throw new FileIOException("Failed to read file: " + path, e);
        }
    }

    @Override
    public Instant lastModified() {
        try {
            return Instant.ofEpochMilli(Files.getLastModifiedTime(path).toMillis());
        } catch (IOException e) {
            throw new FileIOException("Failed to get last modified time: " + path, e);
        }
    }

    @Override
    public long size() {
        try {
            return fileChannel.size();
        } catch (IOException e) {
            throw new FileIOException("Failed to get file channel size: " + path, e);
        }
    }

    @Override
    public void write(Buffer source) {
        ByteBuffer buffer = Buffers.readableByteBuffer(source);
        while (buffer.hasRemaining()) {
            write(buffer);
        }
    }

    @Override
    public void write(long position, Buffer source) {
        Assert.checkArgument(position >= 0, "position must be greater than or equal to zero");

        ByteBuffer buffer = Buffers.readableByteBuffer(source);
        long nextPosition = position;
        while (buffer.hasRemaining()) {
            nextPosition += write(buffer, nextPosition);
        }
    }

    @Override
    public synchronized long append(Buffer source) {
        long offset = size();
        write(offset, source);
        return offset;
    }

    @Override
    public void truncate(long size) {
        try {
            fileChannel.truncate(size);
        } catch (IOException e) {
            throw new FileIOException("Failed to truncate file: " + path, e);
        }
    }

    @Override
    public void force(boolean metadata) {
        try {
            fileChannel.force(metadata);
        } catch (IOException e) {
            throw new FileIOException("Failed to force file: " + path, e);
        }
    }

    @Override
    public FileLock lock() {
        try {
            return fileChannel.lock();
        } catch (IOException e) {
            throw new FileIOException("Failed to lock file: " + path, e);
        }
    }

    @Override
    public @Nullable FileLock tryLock() {
        try {
            return fileChannel.tryLock();
        } catch (IOException e) {
            throw new FileIOException("Failed to try lock file: " + path, e);
        }
    }

    @Override
    public void close() {
        try {
            fileChannel.close();
        } catch (IOException e) {
            throw new FileIOException("Failed to close file channel: " + path, e);
        }
    }

    private int write(ByteBuffer source) {
        try {
            return fileChannel.write(source);
        } catch (IOException e) {
            throw new FileIOException("Failed to write file: " + path, e);
        }
    }

    private int write(ByteBuffer source, long position) {
        try {
            return fileChannel.write(source, position);
        } catch (IOException e) {
            throw new FileIOException("Failed to write file: " + path, e);
        }
    }
}
