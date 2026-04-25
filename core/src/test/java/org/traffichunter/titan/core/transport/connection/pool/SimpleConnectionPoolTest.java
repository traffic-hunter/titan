package org.traffichunter.titan.core.transport.connection.pool;

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import org.traffichunter.titan.core.channel.Channel;
import org.traffichunter.titan.core.transport.connection.Connection;
import org.traffichunter.titan.core.transport.connection.ConnectionFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author yun
 */
class SimpleConnectionPoolTest {

    @Test
    void should_create_min_connections_on_initialization() {
        TestConnectionFactory factory = new TestConnectionFactory();
        Metadata metadata = Metadata.builder()
            .minConnections(2)
            .maxConnections(4)
            .fair(true)
            .build();

        SimpleConnectionPool<TestConnection> pool = new SimpleConnectionPool<>(metadata, factory);

        assertThat(pool.size()).isEqualTo(2);
        assertThat(factory.createdCount()).isEqualTo(2);
    }

    @Test
    void should_acquire_idle_then_create_new_connection_when_idle_pool_is_empty() {
        TestConnectionFactory factory = new TestConnectionFactory();
        Metadata metadata = Metadata.builder()
            .minConnections(1)
            .maxConnections(2)
            .fair(true)
            .build();
        SimpleConnectionPool<TestConnection> pool = new SimpleConnectionPool<>(metadata, factory);

        TestConnection first = pool.acquire();
        TestConnection second = pool.acquire();

        assertThat(first.id()).isEqualTo(1);
        assertThat(second.id()).isEqualTo(2);
        assertThat(first).isNotSameAs(second);
        assertThat(pool.size()).isEqualTo(2);
        assertThat(factory.createdCount()).isEqualTo(2);
    }

    @Test
    void should_wait_when_at_max_connections_and_resume_after_release() throws Exception {
        TestConnectionFactory factory = new TestConnectionFactory();
        Metadata metadata = Metadata.builder()
            .minConnections(1)
            .maxConnections(1)
            .fair(true)
            .build();
        SimpleConnectionPool<TestConnection> pool = new SimpleConnectionPool<>(metadata, factory);
        TestConnection inUse = pool.acquire();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            CompletableFuture<TestConnection> waitingAcquire =
                CompletableFuture.supplyAsync(pool::acquire, executor);

            assertThatThrownBy(() -> waitingAcquire.get(120, TimeUnit.MILLISECONDS))
                .isInstanceOf(TimeoutException.class);

            assertThat(pool.release(inUse)).isTrue();

            TestConnection acquiredLater = waitingAcquire.get(1, TimeUnit.SECONDS);
            assertThat(acquiredLater).isSameAs(inUse);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void should_ignore_double_release_and_keep_connection_state_intact() {
        TestConnectionFactory factory = new TestConnectionFactory();
        Metadata metadata = Metadata.builder()
            .minConnections(1)
            .maxConnections(1)
            .fair(true)
            .build();
        SimpleConnectionPool<TestConnection> pool = new SimpleConnectionPool<>(metadata, factory);

        TestConnection connection = pool.acquire();
        assertThat(pool.size()).isEqualTo(1);

        assertThat(pool.release(connection)).isTrue();
        assertThat(pool.release(connection)).isFalse();

        assertThat(factory.destroyedCount()).isZero();
        assertThat(connection.isClosed()).isFalse();
        assertThat(pool.size()).isEqualTo(1);
    }

    @Test
    void should_destroy_idle_connections_on_close_and_reject_future_acquire() throws IOException {
        TestConnectionFactory factory = new TestConnectionFactory();
        Metadata metadata = Metadata.builder()
            .minConnections(2)
            .maxConnections(2)
            .fair(true)
            .build();
        SimpleConnectionPool<TestConnection> pool = new SimpleConnectionPool<>(metadata, factory);

        pool.close();

        assertThat(pool.isClosed()).isTrue();
        assertThat(pool.size()).isEqualTo(0);
        assertThat(factory.destroyedCount()).isEqualTo(2);
        assertThat(factory.destroyedConnections()).allMatch(TestConnection::isClosed);
        assertThatThrownBy(pool::acquire)
            .isExactlyInstanceOf(IllegalStateException.class)
            .hasMessage("Pool is already closed");
    }

    @Test
    void should_destroy_released_connection_after_close() throws IOException {
        TestConnectionFactory factory = new TestConnectionFactory();
        Metadata metadata = Metadata.builder()
            .minConnections(1)
            .maxConnections(1)
            .fair(true)
            .build();
        SimpleConnectionPool<TestConnection> pool = new SimpleConnectionPool<>(metadata, factory);

        TestConnection inUse = pool.acquire();
        pool.close();

        assertThat(pool.release(inUse)).isFalse();
        assertThat(inUse.isClosed()).isTrue();
        assertThat(factory.destroyedCount()).isEqualTo(1);
        assertThat(pool.size()).isEqualTo(0);
    }

    @Test
    void should_rollback_count_when_factory_create_fails() {
        TestConnectionFactory factory = new TestConnectionFactory();
        factory.failNextCreate();
        Metadata metadata = Metadata.builder()
            .minConnections(0)
            .maxConnections(1)
            .fair(true)
            .build();
        SimpleConnectionPool<TestConnection> pool = new SimpleConnectionPool<>(metadata, factory);

        assertThatThrownBy(pool::acquire)
            .isExactlyInstanceOf(IllegalStateException.class)
            .hasCauseExactlyInstanceOf(IllegalStateException.class)
            .hasRootCauseMessage("create failed");
        assertThat(pool.size()).isZero();
    }

    @Test
    void should_reject_invalid_metadata() {
        TestConnectionFactory factory = new TestConnectionFactory();

        assertThatThrownBy(() -> new SimpleConnectionPool<>(
            Metadata.builder().minConnections(0).maxConnections(0).fair(true).build(),
            factory
        ))
            .isExactlyInstanceOf(IllegalArgumentException.class)
            .hasMessage("maxConnections must be greater than 0");

        assertThatThrownBy(() -> new SimpleConnectionPool<>(
            Metadata.builder().minConnections(2).maxConnections(1).fair(true).build(),
            factory
        ))
            .isExactlyInstanceOf(IllegalArgumentException.class)
            .hasMessage("minConnections must be less than or equal to maxConnections");
    }

    @Test
    void should_fail_waiting_acquire_when_pool_is_closed() throws Exception {
        TestConnectionFactory factory = new TestConnectionFactory();
        Metadata metadata = Metadata.builder()
            .minConnections(1)
            .maxConnections(1)
            .fair(true)
            .build();
        SimpleConnectionPool<TestConnection> pool = new SimpleConnectionPool<>(metadata, factory);
        TestConnection inUse = pool.acquire();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            CompletableFuture<TestConnection> waitingAcquire =
                CompletableFuture.supplyAsync(pool::acquire, executor);

            assertThatThrownBy(() -> waitingAcquire.get(120, TimeUnit.MILLISECONDS))
                .isInstanceOf(TimeoutException.class);

            pool.close();

            assertThatThrownBy(() -> waitingAcquire.get(1, TimeUnit.SECONDS))
                .isExactlyInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(IllegalStateException.class)
                .hasRootCauseMessage("Pool is already closed");

            assertThat(pool.release(inUse)).isFalse();
        } finally {
            executor.shutdownNow();
        }
    }

    private static final class TestConnectionFactory implements ConnectionFactory<TestConnection> {
        private final AtomicInteger sequence = new AtomicInteger();
        private final AtomicInteger destroyedCount = new AtomicInteger();
        private final List<TestConnection> destroyedConnections = new ArrayList<>();
        private volatile boolean failNextCreate;

        @Override
        public TestConnection create() {
            if (failNextCreate) {
                failNextCreate = false;
                throw new IllegalStateException("create failed");
            }
            return new TestConnection(sequence.incrementAndGet());
        }

        @Override
        public void destroy(@NonNull TestConnection connection) {
            connection.close();
            destroyedConnections.add(connection);
            destroyedCount.incrementAndGet();
        }

        void failNextCreate() {
            failNextCreate = true;
        }

        int createdCount() {
            return sequence.get();
        }

        int destroyedCount() {
            return destroyedCount.get();
        }

        List<TestConnection> destroyedConnections() {
            return destroyedConnections;
        }
    }

    private static final class TestConnection implements Connection {
        private final int id;
        private volatile Instant lastActivatedAt = Instant.EPOCH;
        private volatile boolean closed;

        private TestConnection(int id) {
            this.id = id;
        }

        int id() {
            return id;
        }

        boolean isClosed() {
            return closed;
        }

        @Override
        public Channel channel() {
            return null;
        }

        @Override
        public String session() {
            return "session-" + id;
        }

        @Override
        public Instant setLastActivatedAt() {
            lastActivatedAt = Instant.now();
            return lastActivatedAt;
        }

        @Override
        public Instant lastActivatedAt() {
            return lastActivatedAt;
        }

        @Override
        public String version() {
            return "1.0";
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
