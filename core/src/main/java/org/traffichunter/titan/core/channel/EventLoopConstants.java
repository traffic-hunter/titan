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

/**
 * @author yungwang-o
 */
public interface EventLoopConstants {

    long DEFAULT_SHUTDOWN_TIME_OUT = 15;

    String TASK_EVENT_LOOP_THREAD_NAME = "TaskEventLoop";

    String WORKER_EVENT_LOOP_THREAD_NAME = "WorkerEventLoopThread";

    String PRIMARY_EVENT_LOOP_THREAD_NAME = "PrimaryEventLoopThread";

    String SECONDARY_EVENT_LOOP_THREAD_NAME = "SecondaryEventLoopThread";
}
