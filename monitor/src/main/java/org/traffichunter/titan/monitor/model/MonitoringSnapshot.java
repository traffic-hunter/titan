package org.traffichunter.titan.monitor.model;

import java.util.List;

public record MonitoringSnapshot(
        ServerSnapshot server,
        JvmSnapshot jvm,
        ChannelWriteSnapshot channelWrites,
        List<QueueSnapshot> queues
) {

    public MonitoringSnapshot(ServerSnapshot server, JvmSnapshot jvm, List<QueueSnapshot> queues) {
        this(server, jvm, new ChannelWriteSnapshot(0, 0, 0), queues);
    }
}
