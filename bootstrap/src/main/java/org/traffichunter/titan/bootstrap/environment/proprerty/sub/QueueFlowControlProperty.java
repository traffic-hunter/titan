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

/** Mutable YAML DTO for destination queue byte limits. */
public final class QueueFlowControlProperty {

    private boolean enabled = true;
    private long maxPendingBytes = 64L * 1024 * 1024;
    private long resumePendingBytes = 48L * 1024 * 1024;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getMaxPendingBytes() {
        return maxPendingBytes;
    }

    public void setMaxPendingBytes(long maxPendingBytes) {
        this.maxPendingBytes = maxPendingBytes;
    }

    public long getResumePendingBytes() {
        return resumePendingBytes;
    }

    public void setResumePendingBytes(long resumePendingBytes) {
        this.resumePendingBytes = resumePendingBytes;
    }
}
