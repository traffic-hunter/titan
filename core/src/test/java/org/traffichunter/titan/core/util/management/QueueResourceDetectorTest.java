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

import java.util.List;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author yun
 */
class QueueResourceDetectorTest {

    @Test
    void detect_and_sort_dispatcher_queue_resources() {
        MBeanServer server = MBeanServerFactory.createMBeanServer();
        DispatcherQueueMbeans.register(server, new TestQueue("/queue/z", 3, 12, 100, 75, true));
        DispatcherQueueMbeans.register(server, new TestQueue("/queue/a", 1, 4, 200, 150, false));

        List<QueueResource> queues = new QueueResourceDetector(server).detect();

        assertThat(queues).containsExactly(
                new QueueResource("/queue/a", 1, 4, 200, 150, false),
                new QueueResource("/queue/z", 3, 12, 100, 75, true)
        );
    }

    private record TestQueue(
            String destination,
            int size,
            long pendingBytes,
            long maxPendingBytes,
            long resumePendingBytes,
            boolean paused
    ) implements DispatcherQueueMbean {

        @Override
        public String getDestination() {
            return destination;
        }

        @Override
        public int getSize() {
            return size;
        }

        @Override
        public long getPendingBytes() {
            return pendingBytes;
        }

        @Override
        public long getMaxPendingBytes() {
            return maxPendingBytes;
        }

        @Override
        public long getResumePendingBytes() {
            return resumePendingBytes;
        }

        @Override
        public boolean isPaused() {
            return paused;
        }
    }
}
