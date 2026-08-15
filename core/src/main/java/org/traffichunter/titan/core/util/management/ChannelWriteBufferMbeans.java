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
import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.StandardMBean;

/**
 * Registers the process-wide channel write buffer metrics MBean.
 *
 * @author yun
 */
public final class ChannelWriteBufferMbeans {

    public static final String DOMAIN = "org.traffichunter.titan";
    public static final String TYPE = "ChannelWriteBuffer";

    public static ObjectName objectName() {
        try {
            return new ObjectName(DOMAIN + ":type=" + TYPE);
        } catch (JMException e) {
            throw new IllegalStateException("Invalid channel write buffer MBean name", e);
        }
    }

    public static ObjectName register(ChannelWriteBufferMbean metrics) {
        return register(ManagementFactory.getPlatformMBeanServer(), metrics);
    }

    public static ObjectName register(MBeanServer server, ChannelWriteBufferMbean metrics) {
        ObjectName name = objectName();
        try {
            if (!server.isRegistered(name)) {
                server.registerMBean(new StandardMBean(metrics, ChannelWriteBufferMbean.class), name);
            }
            return name;
        } catch (JMException e) {
            throw new IllegalStateException("Failed to register channel write buffer MBean", e);
        }
    }

    private ChannelWriteBufferMbeans() {
    }
}
