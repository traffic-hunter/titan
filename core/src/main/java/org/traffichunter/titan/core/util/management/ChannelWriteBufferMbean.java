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

/**
 * Aggregate management view of every active channel write buffer.
 *
 * <p>The view intentionally avoids one MBean per network connection. This keeps
 * management overhead stable while still exposing outbound pressure across the
 * process.</p>
 *
 * @author yun
 */
public interface ChannelWriteBufferMbean {

    /**
     * Returns the number of open channel write buffers attached to this event-loop group.
     *
     * <p>One active buffer corresponds to one registered network channel. The value decreases
     * when the channel closes and its write buffer releases any remaining outbound data.</p>
     */
    int getActiveBuffers();

    /**
     * Returns bytes accepted for outbound delivery but not yet written to socket buffers.
     *
     * <p>The unit is bytes. The value decreases as partial or complete socket writes make
     * progress; it does not represent application payload size after the write has completed.</p>
     */
    long getPendingBytes();

    /**
     * Returns the number of buffers currently above their configured high watermark.
     *
     * <p>A buffer enters this state after pending bytes exceed the high watermark and leaves it
     * only after pending bytes fall below the low watermark. This is Titan write-buffer pressure,
     * not the operating system selector's socket-writability state.</p>
     */
    int getNonWritableBuffers();
}
