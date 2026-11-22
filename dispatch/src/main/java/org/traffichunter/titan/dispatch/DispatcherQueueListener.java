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
 * Told when a {@link PathFinder} creates or removes a queue.
 *
 * <p>The removal of a queue is never reported before its creation. Both calls are made on the
 * thread that creates or removes the queue, so implementations must not block.</p>
 *
 * @author yun
 */
public interface DispatcherQueueListener {

    /** Listener that ignores every queue. */
    DispatcherQueueListener NOOP = new DispatcherQueueListener() {
        @Override
        public void queueCreated(DispatcherQueue queue) { }

        @Override
        public void queueRemoved(DispatcherQueue queue) { }
    };

    void queueCreated(DispatcherQueue queue);

    void queueRemoved(DispatcherQueue queue);
}
