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

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.traffichunter.titan.core.util.buffer.Buffer;

/**
 * File-system utilities used by backup and persistence code.
 *
 * <p>Methods that receive a {@link Buffer} never release it. Methods that return a
 * {@code Buffer} allocate a new buffer and transfer release ownership to the caller.</p>
 *
 * @author yun
 */
public final class FileHandler {

    private static final String SIGNATURE = "titan";
    private static final String AOF_SUFFIX = ".aof";

    public static Path resolveDestinationFile(String destination) {
        return resolveDestinationFile(Path.of("."), destination);
    }

    public static Path resolveDestinationFile(Path directory, String destination) {
        String destinationPathName = destination.replace('/', '_').trim();
        return directory.resolve(SIGNATURE + destinationPathName + AOF_SUFFIX);
    }

    public static void createParentDirectories(Path path) {
        Path parent = path.getParent();
        if (parent != null) {
            try {
                Files.createDirectories(parent);
            } catch (IOException e) {
                throw new FileIOException("Failed to create parent directories: " + path, e);
            }
        }
    }

    public static Path parentOrCurrent(Path path) {
        Path parent = path.getParent();
        return parent == null ? Path.of(".") : parent;
    }

    public static void createDirectories(Path directory) {
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new FileIOException("Failed to create directories: " + directory, e);
        }
    }

    public static boolean exists(Path path) {
        return Files.exists(path);
    }

    public static boolean isFile(Path path) {
        return Files.isRegularFile(path);
    }

    public static boolean isDirectory(Path path) {
        return Files.isDirectory(path);
    }

    public static void delete(Path path) {
        try {
            Files.delete(path);
        } catch (IOException e) {
            throw new FileIOException("Failed to delete file: " + path, e);
        }
    }

    public static void deleteIfExists(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            throw new FileIOException("Failed to delete file: " + path, e);
        }
    }

    public static void move(Path source, Path target, CopyOption... options) {
        createParentDirectories(target);
        try {
            Files.move(source, target, options);
        } catch (IOException e) {
            throw new FileIOException("Failed to move file: " + source + " -> " + target, e);
        }
    }

    public static void copy(Path source, Path target, CopyOption... options) {
        createParentDirectories(target);
        try {
            Files.copy(source, target, options);
        } catch (IOException e) {
            throw new FileIOException("Failed to copy file: " + source + " -> " + target, e);
        }
    }

    public static void atomicReplace(Path target, Path replacement) {
        move(replacement, target, ATOMIC_MOVE, REPLACE_EXISTING);
    }

    public static Path createTempFile(Path directory, String prefix, String suffix) {
        createDirectories(directory);
        try {
            return Files.createTempFile(directory, prefix, suffix);
        } catch (IOException e) {
            throw new FileIOException("Failed to create temp file: " + directory, e);
        }
    }

    /**
     * Reads the whole file into a newly allocated buffer.
     *
     * @return buffer owned by the caller
     */
    public static Buffer read(Path path) {
        try (FileHandle file = FileHandle.openReadOnly(path)) {
            return file.readAll();
        }
    }

    /**
     * Reads up to {@code length} bytes from {@code position} into a newly allocated buffer.
     *
     * @return buffer owned by the caller
     */
    public static Buffer read(Path path, long position, int length) {
        try (FileHandle file = FileHandle.openReadOnly(path)) {
            return file.read(position, length);
        }
    }

    /**
     * Replaces the file content with the readable bytes of {@code content}.
     *
     * <p>The content buffer remains owned by the caller and is not released.</p>
     */
    public static void write(Path path, Buffer content) {
        try (FileHandle file = FileHandle.open(path, CREATE, WRITE, TRUNCATE_EXISTING)) {
            file.write(content);
            file.force(true);
        }
    }

    /**
     * Appends the readable bytes of {@code content} to the file.
     *
     * <p>The content buffer remains owned by the caller and is not released.</p>
     *
     * @return start offset where the bytes were appended
     */
    public static long append(Path path, Buffer content) {
        try (FileHandle file = FileHandle.open(path, CREATE, READ, WRITE)) {
            long offset = file.append(content);
            file.force(true);
            return offset;
        }
    }

    /**
     * Atomically replaces the file content with the readable bytes of {@code content}.
     *
     * <p>The content buffer remains owned by the caller and is not released. Temporary files are
     * cleaned up when the replace step is not reached.</p>
     */
    public static void atomicWrite(Path path, Buffer content) {
        Path directory = parentOrCurrent(path);
        Path temp = createTempFile(directory, "." + path.getFileName(), ".tmp");
        boolean moved = false;
        try {
            try (FileHandle file = FileHandle.open(temp, WRITE, TRUNCATE_EXISTING)) {
                file.write(content);
                file.force(true);
            }
            atomicReplace(path, temp);
            moved = true;
        } finally {
            if (!moved) {
                deleteIfExists(temp);
            }
        }
    }

    public static List<Path> list(Path directory) {
        try (Stream<Path> paths = Files.list(directory)) {
            return paths.toList();
        } catch (IOException e) {
            throw new FileIOException("Failed to list directory: " + directory, e);
        }
    }

    private FileHandler() { }
}
