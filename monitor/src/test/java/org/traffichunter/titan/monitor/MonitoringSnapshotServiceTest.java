package org.traffichunter.titan.monitor;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.traffichunter.titan.core.codec.json.Json;
import org.traffichunter.titan.monitor.jmx.cpu.JmxCpuMbeanCollector;
import org.traffichunter.titan.monitor.jmx.heap.JmxHeapMbeanCollector;
import org.traffichunter.titan.monitor.jmx.queue.JmxDispatcherQueueCollector;
import org.traffichunter.titan.monitor.jmx.thread.JmxThreadMbeanCollector;
import org.traffichunter.titan.monitor.model.MonitoringSnapshot;

class MonitoringSnapshotServiceTest {

    @Test
    void snapshot_contains_server_jvm_and_queue_sections() {
        Instant startedAt = Instant.parse("2026-06-06T00:00:00Z");
        MonitoringSnapshotService service = new MonitoringSnapshotService(
                Clock.fixed(startedAt.plusSeconds(5), ZoneOffset.UTC),
                startedAt,
                "0.6.1",
                new JmxCpuMbeanCollector(),
                new JmxHeapMbeanCollector(),
                new JmxThreadMbeanCollector(),
                new JmxDispatcherQueueCollector()
        );

        MonitoringSnapshot snapshot = service.snapshot();
        String json = Json.serialize(snapshot);

        assertThat(snapshot.server().version()).isEqualTo("0.6.1");
        assertThat(snapshot.server().uptimeMillis()).isEqualTo(5000);
        assertThat(snapshot.jvm()).isNotNull();
        assertThat(snapshot.queues()).isNotNull();
        assertThat(json).contains("\"server\"", "\"jvm\"", "\"queues\"");
    }
}
