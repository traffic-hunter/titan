package org.traffichunter.titan.core.test.implementation;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.traffichunter.titan.core.util.buffer.Buffer;
import org.traffichunter.titan.core.util.file.FileHandle;
import org.traffichunter.titan.core.util.file.FileUtils;

import static org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;

/**
 * Verifies local file management operations used by backup and persistence.
 *
 * @author yun
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class FileUtilsTest {

    @TempDir
    private Path tempDir;

    @Test
    void create_directories_and_open_create_file() {
        Path file = tempDir.resolve("data/queue.log");
        Buffer content = Buffer.alloc("hello", UTF_8);

        try (FileHandle resource = FileHandle.open(file, CREATE, WRITE)) {
            resource.write(content);
        } finally {
            content.release();
        }

        assertThat(FileUtils.exists(file)).isTrue();
        assertThat(FileUtils.isFile(file)).isTrue();
    }

    @Test
    void append_returns_start_offset_and_read_reads_positioned_bytes() {
        Path file = tempDir.resolve("queue.log");
        Buffer first = Buffer.alloc("hello", UTF_8);
        Buffer second = Buffer.alloc(" titan", UTF_8);
        Buffer buffer = null;

        try (FileHandle resource = FileHandle.open(file, CREATE, READ, WRITE)) {
            long firstOffset = resource.append(first);
            long secondOffset = resource.append(second);

            buffer = resource.read(0, 11);

            assertThat(firstOffset).isZero();
            assertThat(secondOffset).isEqualTo(5);
            assertThat(resource.lastModified()).isNotNull();
            assertThat(buffer.toString(UTF_8)).isEqualTo("hello titan");
        } finally {
            first.release();
            second.release();
            if (buffer != null) {
                buffer.release();
            }
        }
    }

    @Test
    void positioned_write_replaces_content_at_offset() throws Exception {
        Path file = tempDir.resolve("queue.log");
        Buffer content = Buffer.alloc("hello", UTF_8);
        Buffer replacement = Buffer.alloc("y", UTF_8);

        try (FileHandle resource = FileHandle.open(file, CREATE, READ, WRITE, TRUNCATE_EXISTING)) {
            resource.write(content);
            resource.write(0, replacement);
        } finally {
            content.release();
            replacement.release();
        }

        assertThat(Files.readString(file)).isEqualTo("yello");
    }

    @Test
    void truncate_reduces_file_size() {
        Path file = tempDir.resolve("queue.log");
        Buffer content = Buffer.alloc("hello", UTF_8);

        try (FileHandle resource = FileHandle.open(file, CREATE, READ, WRITE, TRUNCATE_EXISTING)) {
            resource.write(content);
            resource.truncate(2);

            assertThat(resource.size()).isEqualTo(2);
        } finally {
            content.release();
        }
    }

    @Test
    void move_copy_delete_and_list_files() throws Exception {
        Path source = tempDir.resolve("source.log");
        Path copied = tempDir.resolve("copy.log");
        Path moved = tempDir.resolve("nested/moved.log");
        Files.writeString(source, "data");

        FileUtils.copy(source, copied);
        FileUtils.move(copied, moved);
        List<Path> files = FileUtils.list(tempDir);
        FileUtils.deleteIfExists(source);

        assertThat(FileUtils.exists(moved)).isTrue();
        assertThat(FileUtils.exists(source)).isFalse();
        assertThat(files).contains(source);
    }

    @Test
    void create_temp_file_creates_file_under_directory() {
        Path directory = tempDir.resolve("tmp");

        Path temp = FileUtils.createTempFile(directory, "backup-", ".tmp");

        assertThat(temp).hasParent(directory);
        assertThat(FileUtils.exists(temp)).isTrue();
    }

    @Test
    void atomic_write_replaces_file_content() throws Exception {
        Path file = tempDir.resolve("manifest.json");
        Files.writeString(file, "old");
        Buffer content = Buffer.alloc("new", UTF_8);

        try {
            FileUtils.atomicWrite(file, content);
        } finally {
            content.release();
        }

        assertThat(Files.readString(file)).isEqualTo("new");
        assertThat(FileUtils.list(tempDir))
                .noneMatch(fileItem -> fileItem.getFileName().toString().startsWith(".manifest.json")
                        && fileItem.getFileName().toString().endsWith(".tmp"));
    }

    @Test
    void utility_read_write_and_append_handle_whole_file_buffers() {
        Path file = tempDir.resolve("data.log");
        Buffer first = Buffer.alloc("hello", UTF_8);
        Buffer second = Buffer.alloc(" titan", UTF_8);
        Buffer read = null;

        try {
            FileUtils.write(file, first);
            long offset = FileUtils.append(file, second);
            read = FileUtils.read(file);

            assertThat(offset).isEqualTo(5);
            assertThat(read.toString(UTF_8)).isEqualTo("hello titan");
        } finally {
            first.release();
            second.release();
            if (read != null) {
                read.release();
            }
        }
    }

    @Test
    void lock_acquires_file_lock() throws Exception {
        Path file = tempDir.resolve("queue.log");

        try (FileHandle resource = FileHandle.open(file, CREATE, READ, WRITE);
             FileLock lock = resource.lock()) {
            assertThat(lock.isValid()).isTrue();
        }
    }

}
