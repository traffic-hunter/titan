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
import java.util.Set;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;

/**
 * Detects aggregate channel write buffer usage from JMX.
 *
 * @author yun
 */
public final class ChannelWriteBufferResourceDetector implements ResourceDetector<ChannelWriteBufferResource> {

    private final MBeanServerConnection server;

    public ChannelWriteBufferResourceDetector() {
        this(ManagementFactory.getPlatformMBeanServer());
    }

    public ChannelWriteBufferResourceDetector(MBeanServerConnection server) {
        this.server = server;
    }

    @Override
    public ChannelWriteBufferResource detect() {
        try {
            Set<ObjectName> names = server.queryNames(ChannelWriteBufferMbeans.objectNamePattern(), null);
            int activeBuffers = 0;
            long pendingBytes = 0;
            int nonWritableBuffers = 0;
            for (ObjectName name : names) {
                activeBuffers = Math.addExact(
                        activeBuffers,
                        attribute(name, "ActiveBuffers", Integer.class)
                );
                pendingBytes = Math.addExact(
                        pendingBytes,
                        attribute(name, "PendingBytes", Long.class)
                );
                nonWritableBuffers = Math.addExact(
                        nonWritableBuffers,
                        attribute(name, "NonWritableBuffers", Integer.class)
                );
            }
            return new ChannelWriteBufferResource(activeBuffers, pendingBytes, nonWritableBuffers);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to detect channel write buffer resources", e);
        }
    }

    private <T> T attribute(ObjectName name, String attribute, Class<T> type) throws Exception {
        Object value = server.getAttribute(name, attribute);
        if (value == null) {
            throw new IllegalStateException("Missing channel write buffer MBean attribute: " + attribute);
        }
        return type.cast(value);
    }
}
