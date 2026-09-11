package org.traffichunter.titan.core.util.management;

import static org.assertj.core.api.Assertions.assertThat;

import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.ObjectName;
import org.junit.jupiter.api.Test;

/**
 * @author yun
 */
class DispatcherQueueMbeansTest {

    @Test
    void unregistering_a_queue_that_lost_its_name_leaves_the_replacement() {
        MBeanServer server = MBeanServerFactory.createMBeanServer();
        TestQueue stale = new TestQueue("/queue/price");
        TestQueue replacement = new TestQueue("/queue/price");
        ObjectName name = DispatcherQueueMbeans.objectName(stale.getDestination());

        DispatcherQueueMbeans.register(server, stale);
        DispatcherQueueMbeans.register(server, replacement);

        // The stale queue left the dispatcher before its removal ran, so it owns nothing now.
        DispatcherQueueMbeans.unregister(server, stale);

        assertThat(server.isRegistered(name)).isTrue();
    }

    @Test
    void unregistering_the_current_queue_removes_its_mbean() {
        MBeanServer server = MBeanServerFactory.createMBeanServer();
        TestQueue queue = new TestQueue("/queue/price");
        ObjectName name = DispatcherQueueMbeans.objectName(queue.getDestination());

        DispatcherQueueMbeans.register(server, queue);
        DispatcherQueueMbeans.unregister(server, queue);

        assertThat(server.isRegistered(name)).isFalse();
    }

    @Test
    void unregistering_by_name_removes_whichever_queue_holds_it() {
        MBeanServer server = MBeanServerFactory.createMBeanServer();
        TestQueue queue = new TestQueue("/queue/price");
        ObjectName name = DispatcherQueueMbeans.objectName(queue.getDestination());

        DispatcherQueueMbeans.register(server, queue);
        DispatcherQueueMbeans.unregister(server, DispatcherQueueMbean.DEFAULT_GROUP, queue.getDestination());

        assertThat(server.isRegistered(name)).isFalse();
        // The name is free, so a later removal of the same queue is a no-op rather than an error.
        DispatcherQueueMbeans.unregister(server, queue);
    }

    @Test
    void queues_with_the_same_name_on_different_servers_are_tracked_apart() {
        MBeanServer first = MBeanServerFactory.createMBeanServer();
        MBeanServer second = MBeanServerFactory.createMBeanServer();
        TestQueue queue = new TestQueue("/queue/price");
        ObjectName name = DispatcherQueueMbeans.objectName(queue.getDestination());

        DispatcherQueueMbeans.register(first, queue);
        DispatcherQueueMbeans.register(second, queue);
        DispatcherQueueMbeans.unregister(second, queue);

        assertThat(first.isRegistered(name)).isTrue();
        assertThat(second.isRegistered(name)).isFalse();
    }

    private static final class TestQueue implements DispatcherQueueMbean {

        private final String destination;

        private TestQueue(String destination) {
            this.destination = destination;
        }

        @Override
        public String getDestination() {
            return destination;
        }

        @Override
        public int getSize() {
            return 0;
        }

        @Override
        public long getPendingBytes() {
            return 0;
        }

        @Override
        public long getMaxPendingBytes() {
            return 0;
        }

        @Override
        public long getResumePendingBytes() {
            return 0;
        }

        @Override
        public boolean isPaused() {
            return false;
        }
    }
}
