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
