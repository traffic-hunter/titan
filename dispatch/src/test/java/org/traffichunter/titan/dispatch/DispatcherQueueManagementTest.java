package org.traffichunter.titan.dispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.management.ManagementFactory;
import java.util.List;
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
    void trie_dispatcher_applies_default_byte_limit_to_automatic_queue() {
        Dispatcher dispatcher = new TrieDispatcher(128);
        Destination destination = Destination.create("/queue/automatic-trie");

        DispatcherQueue queue = dispatcher.getOrPut(destination);

        assertThat(queue.getMaxPendingBytes()).isEqualTo(128);
        assertThat(queue.getResumePendingBytes()).isEqualTo(96);
    }

    @Test
    void map_dispatcher_applies_default_byte_limit_to_automatic_queue() {
        Dispatcher dispatcher = new MapDispatcher(1, 256);
        Destination destination = Destination.create("/queue/automatic-map");

        DispatcherQueue queue = dispatcher.getOrPut(destination);

        assertThat(queue.getMaxPendingBytes()).isEqualTo(256);
        assertThat(queue.getResumePendingBytes()).isEqualTo(192);
    }

    @Test
    void map_dispatcher_returns_created_queue_with_requested_byte_limit() {
        Dispatcher dispatcher = new MapDispatcher(1);
        Destination destination = Destination.create("/queue/orders");

        DispatcherQueue queue = dispatcher.getOrPut(destination, 32);

        assertThat(queue).isNotNull();
        assertThat(queue.getMaxPendingBytes()).isEqualTo(32);
        assertThat(dispatcher.get(destination)).isSameAs(queue);
    }

    @Test
    void trie_dispatcher_returns_created_queue_with_requested_byte_limit() {
        Dispatcher dispatcher = new TrieDispatcher();
        Destination destination = Destination.create("/queue/payments");

        DispatcherQueue queue = dispatcher.getOrPut(destination, 64);

        assertThat(queue).isNotNull();
        assertThat(queue.getMaxPendingBytes()).isEqualTo(64);
        assertThat(dispatcher.get(destination)).isSameAs(queue);
    }

    @Test
    void trie_dispatcher_routes_exact_destinations_under_same_prefix() {
        Dispatcher dispatcher = new TrieDispatcher();
        Destination parent = Destination.create("/queue/orders");
        Destination child = Destination.create("/queue/orders/created");
        Destination sibling = Destination.create("/queue/orders/cancelled");

        DispatcherQueue parentQueue = dispatcher.getOrPut(parent, 10);
        DispatcherQueue childQueue = dispatcher.getOrPut(child, 20);
        DispatcherQueue siblingQueue = dispatcher.getOrPut(sibling, 30);

        assertThat(dispatcher.get(parent)).isSameAs(parentQueue);
        assertThat(dispatcher.get(child)).isSameAs(childQueue);
        assertThat(dispatcher.get(sibling)).isSameAs(siblingQueue);
        assertThat(parentQueue).isNotSameAs(childQueue);
        assertThat(childQueue).isNotSameAs(siblingQueue);
    }

    @Test
    void trie_dispatcher_does_not_route_unknown_child_to_parent_queue() {
        Dispatcher dispatcher = new TrieDispatcher();
        Destination parent = Destination.create("/queue/orders");
        Destination unknownChild = Destination.create("/queue/orders/unknown");

        dispatcher.getOrPut(parent, 10);

        assertThat(dispatcher.get(unknownChild)).isNull();
    }

    @Test
    void trie_dispatcher_searchAll_returns_exact_destination_queue() {
        Dispatcher dispatcher = new TrieDispatcher();
        Destination destination = Destination.create("/queue/orders");

        DispatcherQueue queue = dispatcher.getOrPut(destination, 10);

        assertThat(dispatcher.searchAll(destination)).containsExactly(queue);
    }

    @Test
    void trie_dispatcher_searchAll_routes_wildcard_to_descendant_queues() {
        Dispatcher dispatcher = new TrieDispatcher();
        Destination parent = Destination.create("/queue/orders");
        Destination created = Destination.create("/queue/orders/created");
        Destination cancelled = Destination.create("/queue/orders/cancelled");
        Destination other = Destination.create("/queue/payments");

        dispatcher.getOrPut(parent, 10);
        DispatcherQueue createdQueue = dispatcher.getOrPut(created, 20);
        DispatcherQueue cancelledQueue = dispatcher.getOrPut(cancelled, 30);
        dispatcher.getOrPut(other, 40);

        List<DispatcherQueue> dispatcherQueues = dispatcher.searchAll(Destination.create("/queue/orders/*"));
        assertThat(dispatcherQueues).hasSize(2);
        assertThat(dispatcherQueues).containsExactlyInAnyOrder(createdQueue, cancelledQueue);
    }

    @Test
    void map_dispatcher_searchAll_routes_wildcard_to_descendant_queues() {
        Dispatcher dispatcher = new MapDispatcher(4);
        Destination parent = Destination.create("/queue/orders");
        Destination created = Destination.create("/queue/orders/created");
        Destination cancelled = Destination.create("/queue/orders/cancelled");
        Destination other = Destination.create("/queue/payments");

        dispatcher.getOrPut(parent, 10);
        DispatcherQueue createdQueue = dispatcher.getOrPut(created, 20);
        DispatcherQueue cancelledQueue = dispatcher.getOrPut(cancelled, 30);
        dispatcher.getOrPut(other, 40);

        assertThat(dispatcher.searchAll(Destination.create("/queue/orders/*")))
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
    void trie_dispatcher_serves_its_own_group_only() {
        Dispatcher dispatcher = new TrieDispatcher();
        Destination destination = Destination.create("/queue/own-group");

        DispatcherQueue queue = dispatcher.getOrPut(DestinationGroups.DEFAULT, destination);

        assertThat(queue).isSameAs(dispatcher.get(destination));
        assertThat(dispatcher.get(DestinationGroups.DEFAULT, destination)).isSameAs(queue);
        assertThatThrownBy(() -> dispatcher.getOrPut("market", destination))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> dispatcher.get("market", destination))
                .isInstanceOf(UnsupportedOperationException.class);

        DispatcherQueueMbeans.unregister(destination.path());
    }

    @Test
    void conditional_remove_keeps_a_queue_recreated_since_the_lookup() {
        Destination destination = Destination.create("/queue/stale-remove");
        TrieDispatcher dispatcher = new TrieDispatcher();
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        ObjectName name = DispatcherQueueMbeans.objectName(destination.path());

        DispatcherQueue stale = dispatcher.getOrPut(destination);
        dispatcher.remove(destination);
        DispatcherQueue replacement = dispatcher.getOrPut(destination);

        // A delete that started before the replacement existed must leave it alone,
        // including its MBean, which shares the object name with the stale queue.
        assertThat(dispatcher.remove(stale)).isFalse();
        assertThat(dispatcher.get(destination)).isSameAs(replacement);
        assertThat(server.isRegistered(name)).isTrue();

        assertThat(dispatcher.remove(replacement)).isTrue();
        assertThat(dispatcher.get(destination)).isNull();
        assertThat(server.isRegistered(name)).isFalse();
    }

    @Test
    void map_dispatcher_unregisters_the_mbean_of_a_removed_queue() {
        Destination destination = Destination.create("/queue/map-mbean");
        Dispatcher dispatcher = new MapDispatcher(4);
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        ObjectName name = DispatcherQueueMbeans.objectName(destination.path());

        DispatcherQueue queue = dispatcher.getOrPut(destination);
        assertThat(server.isRegistered(name)).isTrue();

        dispatcher.remove(destination);

        assertThat(dispatcher.get(destination)).isNull();
        assertThat(server.isRegistered(name)).isFalse();

        DispatcherQueue replacement = dispatcher.getOrPut(destination);
        assertThat(dispatcher.remove(queue)).isFalse();
        assertThat(server.isRegistered(name)).isTrue();

        assertThat(dispatcher.remove(replacement)).isTrue();
        assertThat(server.isRegistered(name)).isFalse();
    }
}
