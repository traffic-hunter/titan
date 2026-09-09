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
