/*
The MIT License

Copyright (c) 2025 traffic-hunter

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
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
