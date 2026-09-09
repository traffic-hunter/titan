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

import java.lang.management.ThreadMXBean;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author yun
 */
class ThreadResourceDetectorTest {

    @Test
    void detect_thread_resource_from_thread_management_bean() {
        ThreadMXBean threadMXBean = mock(ThreadMXBean.class);
        when(threadMXBean.getThreadCount()).thenReturn(12);
        when(threadMXBean.getPeakThreadCount()).thenReturn(20);
        when(threadMXBean.getTotalStartedThreadCount()).thenReturn(42L);

        ThreadResource thread = new ThreadResourceDetector(threadMXBean).detect();

        assertThat(thread).isEqualTo(new ThreadResource(12, 20, 42));
    }
}
