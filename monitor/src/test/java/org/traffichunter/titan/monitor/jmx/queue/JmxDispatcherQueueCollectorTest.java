package org.traffichunter.titan.monitor.jmx.queue;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import org.junit.jupiter.api.Test;
import org.traffichunter.titan.core.message.Message;
import org.traffichunter.titan.dispatch.DispatcherQueue;
import org.traffichunter.titan.core.util.Destination;
import org.traffichunter.titan.core.util.management.DispatcherQueueMbeans;
import org.traffichunter.titan.monitor.model.QueueSnapshot;

class JmxDispatcherQueueCollectorTest {

    @Test
    void collect_reads_dispatcher_queue_mbean_attributes() {
        MBeanServer server = MBeanServerFactory.createMBeanServer();
        DispatcherQueue queue = DispatcherQueue.create(Destination.create("/queue/orders"), 10);
        queue.enqueue(Message.builder()
                .destination(Destination.create("/queue/orders"))
                .createdAt(Instant.now())
                .producerId("test")
                .body("hello".getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .build());
        queue.pause();
        DispatcherQueueMbeans.register(server, queue);

        List<QueueSnapshot> queues = new JmxDispatcherQueueCollector(server).collect();

        assertThat(queues).containsExactly(new QueueSnapshot("/queue/orders", 1, 5, 10, 8, true));
    }
}
