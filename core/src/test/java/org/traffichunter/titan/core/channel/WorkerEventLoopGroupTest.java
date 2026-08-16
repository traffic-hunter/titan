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
package org.traffichunter.titan.core.channel;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.traffichunter.titan.core.util.concurrent.Promise;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author yun
 */
class WorkerEventLoopGroupTest {

    private WorkerEventLoopGroup group;

    @AfterEach
    void tearDown() {
        if (group != null && !group.isShuttingDown()) {
            group.gracefullyShutdown(1, TimeUnit.SECONDS);
        }
    }

    @Test
    void reject_non_positive_group_size() {
        assertThatThrownBy(() -> new WorkerEventLoopGroup(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("greater than zero");
    }

    @Test
    void select_worker_event_loops_in_round_robin_order() {
        group = new WorkerEventLoopGroup(2);

        TaskEventLoop first = group.next();
        TaskEventLoop second = group.next();
        TaskEventLoop third = group.next();

        assertThat(first).isNotSameAs(second);
        assertThat(third).isSameAs(first);
    }

    @Test
    @Timeout(5)
    void execute_submitted_and_scheduled_tasks() throws Exception {
        group = new WorkerEventLoopGroup(2);
        group.start();

        Promise<String> submitted = group.submit(() -> {
            assertThat(group.inEventLoop()).isTrue();
            return Thread.currentThread().getName();
        });
        Promise<Integer> scheduled = group.schedule(() -> 42, 10, TimeUnit.MILLISECONDS);

        assertThat(submitted.get(3, TimeUnit.SECONDS)).startsWith("WorkerEventLoopThread-");
        assertThat(scheduled.get(3, TimeUnit.SECONDS)).isEqualTo(42);
    }

    @Test
    @Timeout(5)
    void manage_member_lifecycle_as_a_group() {
        group = new WorkerEventLoopGroup(2);

        assertThat(group.isNotStarted()).isTrue();

        group.start();
        assertThat(group.isStarted()).isTrue();

        group.gracefullyShutdown(1, TimeUnit.SECONDS);
        Awaitility.await().atMost(3, TimeUnit.SECONDS).until(group::isShutdown);
    }
}
