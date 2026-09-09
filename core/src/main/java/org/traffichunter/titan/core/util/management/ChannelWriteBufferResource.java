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
 * Process-wide channel write-buffer pressure aggregated from all active event-loop groups.
 *
 * @param activeBuffers number of open channel write buffers
 * @param pendingBytes bytes accepted for outbound delivery but not yet written to sockets
 * @param nonWritableBuffers buffers above their high watermark and not yet below their low watermark
 * @author yun
 */
public record ChannelWriteBufferResource(
        int activeBuffers,
        long pendingBytes,
        int nonWritableBuffers
) {
}
