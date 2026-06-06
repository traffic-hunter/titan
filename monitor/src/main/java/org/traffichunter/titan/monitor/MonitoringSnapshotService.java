package org.traffichunter.titan.monitor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.traffichunter.titan.monitor.jmx.cpu.JmxCpuMbeanCollector;
import org.traffichunter.titan.monitor.jmx.heap.JmxHeapMbeanCollector;
import org.traffichunter.titan.monitor.jmx.queue.JmxDispatcherQueueCollector;
import org.traffichunter.titan.monitor.jmx.thread.JmxThreadMbeanCollector;
import org.traffichunter.titan.monitor.model.JvmSnapshot;
import org.traffichunter.titan.monitor.model.MonitoringSnapshot;
import org.traffichunter.titan.monitor.model.ServerSnapshot;

public final class MonitoringSnapshotService {

    private final Clock clock;
    private final Instant startedAt;
    private final String version;
    private final JmxCpuMbeanCollector cpuCollector;
    private final JmxHeapMbeanCollector heapCollector;
    private final JmxThreadMbeanCollector threadCollector;
    private final JmxDispatcherQueueCollector queueCollector;

    public MonitoringSnapshotService(String version) {
        this(
                Clock.systemUTC(),
                Instant.now(),
                version,
                new JmxCpuMbeanCollector(),
                new JmxHeapMbeanCollector(),
                new JmxThreadMbeanCollector(),
                new JmxDispatcherQueueCollector()
        );
    }

    public MonitoringSnapshotService(
            Clock clock,
            Instant startedAt,
            String version,
            JmxCpuMbeanCollector cpuCollector,
            JmxHeapMbeanCollector heapCollector,
            JmxThreadMbeanCollector threadCollector,
            JmxDispatcherQueueCollector queueCollector
    ) {
        this.clock = clock;
        this.startedAt = startedAt;
        this.version = version;
        this.cpuCollector = cpuCollector;
        this.heapCollector = heapCollector;
        this.threadCollector = threadCollector;
        this.queueCollector = queueCollector;
    }

    public MonitoringSnapshot snapshot() {
        Instant timestamp = clock.instant();
        return new MonitoringSnapshot(
                new ServerSnapshot(
                        version,
                        startedAt,
                        timestamp,
                        Duration.between(startedAt, timestamp).toMillis()
                ),
                new JvmSnapshot(
                        cpuCollector.collect(),
                        heapCollector.collect(),
                        threadCollector.collect()
                ),
                queueCollector.collect()
        );
    }
}
