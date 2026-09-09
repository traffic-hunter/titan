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

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;

/**
 * Detects JVM thread counts through the platform thread management bean.
 *
 * @author yun
 */
public final class ThreadResourceDetector implements ResourceDetector<ThreadResource> {

    private final ThreadMXBean threadMXBean;

    /**
     * Creates a detector backed by the platform thread management bean.
     */
    public ThreadResourceDetector() {
        this(ManagementFactory.getThreadMXBean());
    }

    /**
     * Creates a detector backed by the supplied thread management bean.
     *
     * @param threadMXBean thread management bean used for measurements
     */
    public ThreadResourceDetector(ThreadMXBean threadMXBean) {
        this.threadMXBean = threadMXBean;
    }

    @Override
    public ThreadResource detect() {
        return new ThreadResource(
                threadMXBean.getThreadCount(),
                threadMXBean.getPeakThreadCount(),
                threadMXBean.getTotalStartedThreadCount()
        );
    }
}
