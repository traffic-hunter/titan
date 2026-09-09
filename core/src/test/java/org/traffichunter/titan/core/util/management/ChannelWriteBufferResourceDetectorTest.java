/*
 * Copyright 2025 traffic-hunter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traffichunter.titan.core.util.management;

import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.ObjectName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author yun
 */
class ChannelWriteBufferResourceDetectorTest {

    @Test
    void detect_aggregate_channel_write_buffer_resource() {
        MBeanServer server = MBeanServerFactory.createMBeanServer();
        ChannelWriteBufferMbeans.register(server, "server", new TestMetrics(4, 1024, 2));
        ChannelWriteBufferMbeans.register(server, "client", new TestMetrics(3, 2048, 1));

        ChannelWriteBufferResource resource = new ChannelWriteBufferResourceDetector(server).detect();

        assertThat(resource).isEqualTo(new ChannelWriteBufferResource(7, 3072, 3));
    }

    @Test
    void return_empty_resource_when_mbean_is_not_registered() {
        MBeanServer server = MBeanServerFactory.createMBeanServer();

        ChannelWriteBufferResource resource = new ChannelWriteBufferResourceDetector(server).detect();

        assertThat(resource).isEqualTo(new ChannelWriteBufferResource(0, 0, 0));
    }

    @Test
    void unregister_only_selected_event_loop_group_metrics() {
        MBeanServer server = MBeanServerFactory.createMBeanServer();
        ObjectName serverMetrics = ChannelWriteBufferMbeans.register(
                server,
                "server",
                new TestMetrics(4, 1024, 2)
        );
        ChannelWriteBufferMbeans.register(server, "client", new TestMetrics(3, 2048, 1));

        ChannelWriteBufferMbeans.unregister(server, serverMetrics);

        ChannelWriteBufferResource resource = new ChannelWriteBufferResourceDetector(server).detect();
        assertThat(resource).isEqualTo(new ChannelWriteBufferResource(3, 2048, 1));
    }

    private record TestMetrics(
            int activeBuffers,
            long pendingBytes,
            int nonWritableBuffers
    ) implements ChannelWriteBufferMbean {

        @Override
        public int getActiveBuffers() {
            return activeBuffers;
        }

        @Override
        public long getPendingBytes() {
            return pendingBytes;
        }

        @Override
        public int getNonWritableBuffers() {
            return nonWritableBuffers;
        }
    }
}
