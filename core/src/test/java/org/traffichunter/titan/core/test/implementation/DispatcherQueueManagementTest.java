package org.traffichunter.titan.core.test.implementation;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.management.ManagementFactory;
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
    void dispatcher_queue_mbean_can_be_unregistered() throws Exception {
        Destination destination = Destination.create("/queue/mbean-test");
        DispatcherQueue queue = DispatcherQueue.create(destination, 10);
        ObjectName name = DispatcherQueueMbeans.objectName(destination.path());

        assertThat(ManagementFactory.getPlatformMBeanServer().isRegistered(name)).isTrue();

        DispatcherQueueMbeans.unregister(queue.getDestination());

        assertThat(ManagementFactory.getPlatformMBeanServer().isRegistered(name)).isFalse();
    }
}
