package org.traffichunter.titan.monitor.model;

import java.util.List;

public record MonitoringSnapshot(ServerSnapshot server, JvmSnapshot jvm, List<QueueSnapshot> queues) {
}
