package org.traffichunter.titan.dispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import org.junit.jupiter.api.Test;
import org.traffichunter.titan.core.util.Destination;
import org.traffichunter.titan.core.util.DestinationGroups;
import org.traffichunter.titan.core.util.management.DispatcherQueueMbeans;

class DispatcherQueueManagementTest {

    @Test
    void dispatcher_queue_uses_unbounded_default_byte_limit() {
        Destination destination = Destination.create("/queue/default-byte-limit");
        DispatcherQueue queue = DispatcherQueue.create(destination);

        assertThat(queue.getMaxPendingBytes()).isEqualTo(Long.MAX_VALUE);

        DispatcherQueueMbeans.unregister(destination.path());
    }

    @Test
    void trie_path_finder_applies_default_byte_limit_to_automatic_queue() {
        PathFinder pathFinder = new TriePathFinder(128);
        Destination destination = Destination.create("/queue/automatic-trie");

        DispatcherQueue queue = pathFinder.getOrPut(destination);

        assertThat(queue.getMaxPendingBytes()).isEqualTo(128);
        assertThat(queue.getResumePendingBytes()).isEqualTo(96);
    }

    @Test
    void map_path_finder_applies_default_byte_limit_to_automatic_queue() {
        PathFinder pathFinder = new MapPathFinder(1, 256);
        Destination destination = Destination.create("/queue/automatic-map");

        DispatcherQueue queue = pathFinder.getOrPut(destination);

        assertThat(queue.getMaxPendingBytes()).isEqualTo(256);
        assertThat(queue.getResumePendingBytes()).isEqualTo(192);
    }

    @Test
    void map_path_finder_returns_created_queue_with_requested_byte_limit() {
        PathFinder pathFinder = new MapPathFinder(1);
        Destination destination = Destination.create("/queue/orders");

        DispatcherQueue queue = pathFinder.getOrPut(destination, 32);

        assertThat(queue).isNotNull();
        assertThat(queue.getMaxPendingBytes()).isEqualTo(32);
        assertThat(pathFinder.get(destination)).isSameAs(queue);
    }

    @Test
    void trie_path_finder_returns_created_queue_with_requested_byte_limit() {
        PathFinder pathFinder = new TriePathFinder();
        Destination destination = Destination.create("/queue/payments");

        DispatcherQueue queue = pathFinder.getOrPut(destination, 64);

        assertThat(queue).isNotNull();
        assertThat(queue.getMaxPendingBytes()).isEqualTo(64);
        assertThat(pathFinder.get(destination)).isSameAs(queue);
    }

    @Test
    void trie_path_finder_routes_exact_destinations_under_same_prefix() {
        PathFinder pathFinder = new TriePathFinder();
        Destination parent = Destination.create("/queue/orders");
        Destination child = Destination.create("/queue/orders/created");
        Destination sibling = Destination.create("/queue/orders/cancelled");

        DispatcherQueue parentQueue = pathFinder.getOrPut(parent, 10);
        DispatcherQueue childQueue = pathFinder.getOrPut(child, 20);
        DispatcherQueue siblingQueue = pathFinder.getOrPut(sibling, 30);

        assertThat(pathFinder.get(parent)).isSameAs(parentQueue);
        assertThat(pathFinder.get(child)).isSameAs(childQueue);
        assertThat(pathFinder.get(sibling)).isSameAs(siblingQueue);
        assertThat(parentQueue).isNotSameAs(childQueue);
        assertThat(childQueue).isNotSameAs(siblingQueue);
    }

    @Test
    void trie_path_finder_does_not_route_unknown_child_to_parent_queue() {
        PathFinder pathFinder = new TriePathFinder();
        Destination parent = Destination.create("/queue/orders");
        Destination unknownChild = Destination.create("/queue/orders/unknown");

        pathFinder.getOrPut(parent, 10);

        assertThat(pathFinder.get(unknownChild)).isNull();
    }

    @Test
    void trie_path_finder_searchAll_returns_exact_destination_queue() {
        PathFinder pathFinder = new TriePathFinder();
        Destination destination = Destination.create("/queue/orders");

        DispatcherQueue queue = pathFinder.getOrPut(destination, 10);

        assertThat(pathFinder.searchAll(destination)).containsExactly(queue);
    }

    @Test
    void trie_path_finder_searchAll_routes_wildcard_to_descendant_queues() {
        PathFinder pathFinder = new TriePathFinder();
        Destination parent = Destination.create("/queue/orders");
        Destination created = Destination.create("/queue/orders/created");
        Destination cancelled = Destination.create("/queue/orders/cancelled");
        Destination other = Destination.create("/queue/payments");

        pathFinder.getOrPut(parent, 10);
        DispatcherQueue createdQueue = pathFinder.getOrPut(created, 20);
        DispatcherQueue cancelledQueue = pathFinder.getOrPut(cancelled, 30);
        pathFinder.getOrPut(other, 40);

        List<DispatcherQueue> dispatcherQueues = pathFinder.searchAll(Destination.create("/queue/orders/*"));
        assertThat(dispatcherQueues).hasSize(2);
        assertThat(dispatcherQueues).containsExactlyInAnyOrder(createdQueue, cancelledQueue);
    }

    @Test
    void map_path_finder_searchAll_routes_wildcard_to_descendant_queues() {
        PathFinder pathFinder = new MapPathFinder(4);
        Destination parent = Destination.create("/queue/orders");
        Destination created = Destination.create("/queue/orders/created");
        Destination cancelled = Destination.create("/queue/orders/cancelled");
        Destination other = Destination.create("/queue/payments");

        pathFinder.getOrPut(parent, 10);
        DispatcherQueue createdQueue = pathFinder.getOrPut(created, 20);
        DispatcherQueue cancelledQueue = pathFinder.getOrPut(cancelled, 30);
        pathFinder.getOrPut(other, 40);

        assertThat(pathFinder.searchAll(Destination.create("/queue/orders/*")))
                .containsExactlyInAnyOrder(createdQueue, cancelledQueue);
    }

    @Test
    void dispatcher_queue_mbean_can_be_unregistered() throws Exception {
        Destination destination = Destination.create("/queue/mbean-test");
        DispatcherQueue queue = DispatcherQueue.create(destination, 10);
        ObjectName name = DispatcherQueueMbeans.objectName(destination.path());

        assertThat(ManagementFactory.getPlatformMBeanServer().isRegistered(name)).isTrue();

        DispatcherQueueMbeans.unregister(queue.getDestination());

        assertThat(ManagementFactory.getPlatformMBeanServer().isRegistered(name)).isFalse();
    }

    @Test
    void trie_path_finder_serves_its_own_group_only() {
        PathFinder pathFinder = new TriePathFinder();
        Destination destination = Destination.create("/queue/own-group");

        DispatcherQueue queue = pathFinder.getOrPut(DestinationGroups.DEFAULT, destination);

        assertThat(queue).isSameAs(pathFinder.get(destination));
        assertThat(pathFinder.get(DestinationGroups.DEFAULT, destination)).isSameAs(queue);
        assertThatThrownBy(() -> pathFinder.getOrPut("market", destination))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> pathFinder.get("market", destination))
                .isInstanceOf(UnsupportedOperationException.class);

        DispatcherQueueMbeans.unregister(destination.path());
    }

    @Test
    void conditional_remove_keeps_a_queue_recreated_since_the_lookup() {
        Destination destination = Destination.create("/queue/stale-remove");
        TriePathFinder pathFinder = new TriePathFinder();
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        ObjectName name = DispatcherQueueMbeans.objectName(destination.path());

        DispatcherQueue stale = pathFinder.getOrPut(destination);
        pathFinder.remove(destination);
        DispatcherQueue replacement = pathFinder.getOrPut(destination);

        // A delete that started before the replacement existed must leave it alone,
        // including its MBean, which shares the object name with the stale queue.
        assertThat(pathFinder.remove(stale)).isFalse();
        assertThat(pathFinder.get(destination)).isSameAs(replacement);
        assertThat(server.isRegistered(name)).isTrue();

        assertThat(pathFinder.remove(replacement)).isTrue();
        assertThat(pathFinder.get(destination)).isNull();
        assertThat(server.isRegistered(name)).isFalse();
    }

    @Test
    void trie_path_finder_removed_queue_leaves_exact_and_wildcard_lookups() {
        TriePathFinder pathFinder = new TriePathFinder();
        Destination created = Destination.create("/queue/removal/created");
        Destination cancelled = Destination.create("/queue/removal/cancelled");
        Destination wildcard = Destination.create("/queue/removal/*");

        pathFinder.getOrPut(created);
        DispatcherQueue cancelledQueue = pathFinder.getOrPut(cancelled);

        pathFinder.remove(created);

        assertThat(pathFinder.get(created)).isNull();
        assertThat(pathFinder.exists(created)).isFalse();
        assertThat(pathFinder.searchAll(wildcard)).containsExactly(cancelledQueue);

        pathFinder.remove(cancelled);

        assertThat(pathFinder.isEmpty()).isTrue();
    }

    @Test
    void trie_path_finder_stale_remove_keeps_the_replacement_in_wildcard_search() {
        TriePathFinder pathFinder = new TriePathFinder();
        Destination destination = Destination.create("/queue/stale-wildcard/created");
        Destination wildcard = Destination.create("/queue/stale-wildcard/*");

        DispatcherQueue stale = pathFinder.getOrPut(destination);
        pathFinder.remove(destination);
        DispatcherQueue replacement = pathFinder.getOrPut(destination);

        assertThat(pathFinder.remove(stale)).isFalse();
        assertThat(pathFinder.searchAll(wildcard)).containsExactly(replacement);

        pathFinder.remove(replacement);
    }

    @Test
    void trie_path_finder_creates_one_queue_for_concurrent_callers() throws Exception {
        TriePathFinder pathFinder = new TriePathFinder();
        Destination destination = Destination.create("/queue/concurrent-create/created");
        CountDownLatch start = new CountDownLatch(1);
        List<Future<DispatcherQueue>> results = new ArrayList<>();

        try (ExecutorService executor = Executors.newFixedThreadPool(8)) {
            for (int i = 0; i < 8; i++) {
                results.add(executor.submit(() -> {
                    start.await();
                    return pathFinder.getOrPut(destination);
                }));
            }
            start.countDown();
        }

        DispatcherQueue queue = pathFinder.get(destination);
        for (Future<DispatcherQueue> result : results) {
            assertThat(result.get()).isSameAs(queue);
        }
        assertThat(pathFinder.searchAll(Destination.create("/queue/concurrent-create/*"))).containsExactly(queue);

        pathFinder.remove(destination);
    }

    @Test
    void trie_path_finder_reports_a_queue_once_when_created_and_once_when_removed() throws Exception {
        TriePathFinder pathFinder = new TriePathFinder();
        List<DispatcherQueue> created = new CopyOnWriteArrayList<>();
        List<DispatcherQueue> removed = new CopyOnWriteArrayList<>();
        pathFinder.listen(recordingListener(created, removed));
        Destination destination = Destination.create("/queue/listener/reported");
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(8)) {
            for (int i = 0; i < 8; i++) {
                executor.submit(() -> {
                    start.await();
                    return pathFinder.getOrPut(destination);
                });
            }
            start.countDown();
        }

        DispatcherQueue queue = pathFinder.get(destination);
        assertThat(created).containsExactly(queue);
        assertThat(removed).isEmpty();

        assertThat(pathFinder.remove(queue)).isTrue();
        assertThat(pathFinder.remove(queue)).isFalse();
        pathFinder.remove(destination);

        assertThat(created).containsExactly(queue);
        assertThat(removed).containsExactly(queue);
    }

    @Test
    void trie_path_finder_reports_existing_queues_to_a_new_listener() {
        TriePathFinder pathFinder = new TriePathFinder();
        Destination destination = Destination.create("/queue/listener/existing");
        DispatcherQueue queue = pathFinder.getOrPut(destination);
        List<DispatcherQueue> created = new CopyOnWriteArrayList<>();

        pathFinder.listen(recordingListener(created, new CopyOnWriteArrayList<>()));

        assertThat(created).containsExactly(queue);

        pathFinder.remove(destination);
    }

    private static DispatcherQueueListener recordingListener(
            List<DispatcherQueue> created,
            List<DispatcherQueue> removed
    ) {
        return new DispatcherQueueListener() {
            @Override
            public void queueCreated(DispatcherQueue queue) {
                created.add(queue);
            }

            @Override
            public void queueRemoved(DispatcherQueue queue) {
                removed.add(queue);
            }
        };
    }

    @Test
    void map_path_finder_unregisters_the_mbean_of_a_removed_queue() {
        Destination destination = Destination.create("/queue/map-mbean");
        PathFinder pathFinder = new MapPathFinder(4);
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        ObjectName name = DispatcherQueueMbeans.objectName(destination.path());

        DispatcherQueue queue = pathFinder.getOrPut(destination);
        assertThat(server.isRegistered(name)).isTrue();

        pathFinder.remove(destination);

        assertThat(pathFinder.get(destination)).isNull();
        assertThat(server.isRegistered(name)).isFalse();

        DispatcherQueue replacement = pathFinder.getOrPut(destination);
        assertThat(pathFinder.remove(queue)).isFalse();
        assertThat(server.isRegistered(name)).isTrue();

        assertThat(pathFinder.remove(replacement)).isTrue();
        assertThat(server.isRegistered(name)).isFalse();
    }
}
