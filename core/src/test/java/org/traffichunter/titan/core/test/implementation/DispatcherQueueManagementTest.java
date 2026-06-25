package org.traffichunter.titan.core.test.implementation;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.management.ManagementFactory;
import java.util.List;
import javax.management.ObjectName;
import org.junit.jupiter.api.Test;
import org.traffichunter.titan.core.message.dispatcher.Dispatcher;
import org.traffichunter.titan.core.message.dispatcher.DispatcherQueue;
import org.traffichunter.titan.core.message.dispatcher.MapDispatcher;
import org.traffichunter.titan.core.message.dispatcher.TrieDispatcher;
import org.traffichunter.titan.core.util.Destination;
import org.traffichunter.titan.core.util.mbeans.DispatcherQueueMbeans;

class DispatcherQueueManagementTest {

    @Test
    void map_dispatcher_returns_created_queue_with_requested_capacity() {
        Dispatcher dispatcher = new MapDispatcher(1);
        Destination destination = Destination.create("/queue/orders");

        DispatcherQueue queue = dispatcher.getOrPut(destination, 32);

        assertThat(queue).isNotNull();
        assertThat(queue.capacity()).isEqualTo(32);
        assertThat(dispatcher.get(destination)).isSameAs(queue);
    }

    @Test
    void trie_dispatcher_returns_created_queue_with_requested_capacity() {
        Dispatcher dispatcher = new TrieDispatcher();
        Destination destination = Destination.create("/queue/payments");

        DispatcherQueue queue = dispatcher.getOrPut(destination, 64);

        assertThat(queue).isNotNull();
        assertThat(queue.capacity()).isEqualTo(64);
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
}
