/*
The MIT License

Copyright (c) 2025 traffic-hunter

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
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
