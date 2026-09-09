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
package org.traffichunter.titan.bootstrap.environment.proprerty.sub;

/** Mutable YAML DTO for process-wide flow-control settings. */
public final class FlowControlProperty {

    private boolean enabled;
    private HeapFlowControlProperty heap;
    private QueueFlowControlProperty queue;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public HeapFlowControlProperty getHeap() {
        return heap;
    }

    public void setHeap(HeapFlowControlProperty heap) {
        this.heap = heap;
    }

    public QueueFlowControlProperty getQueue() {
        return queue;
    }

    public void setQueue(QueueFlowControlProperty queue) {
        this.queue = queue;
    }
}
