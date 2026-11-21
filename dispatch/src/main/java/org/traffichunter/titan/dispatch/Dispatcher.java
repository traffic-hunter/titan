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
package org.traffichunter.titan.dispatch;

/**
 * Takes messages out of destination queues and delivers them to their subscribers.
 *
 * @author yun
 */
public interface Dispatcher extends AutoCloseable {

    /** Starts delivering from the queue. Does nothing when the queue is already being delivered. */
    void start(DispatcherQueue queue);

    /** Stops delivering from the queue. Messages still in it stay there. */
    void stop(DispatcherQueue queue);

    /** Stops delivering from every queue. */
    @Override
    void close();
}
