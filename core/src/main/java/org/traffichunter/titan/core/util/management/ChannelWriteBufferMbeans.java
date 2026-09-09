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

    public static ObjectName objectName(String group) {
        try {
            return new ObjectName(DOMAIN + ":type=" + TYPE + ",group=" + ObjectName.quote(group));
        } catch (JMException e) {
            throw new IllegalStateException("Invalid channel write buffer MBean name", e);
        }
    }

    public static ObjectName objectNamePattern() {
        try {
            return new ObjectName(DOMAIN + ":type=" + TYPE + ",*");
        } catch (JMException e) {
            throw new IllegalStateException("Invalid channel write buffer MBean pattern", e);
        }
    }

    public static ObjectName register(String group, ChannelWriteBufferMbean metrics) {
        return register(ManagementFactory.getPlatformMBeanServer(), group, metrics);
    }

    public static ObjectName register(MBeanServer server, String group, ChannelWriteBufferMbean metrics) {
        ObjectName name = objectName(group);
        try {
            if (!server.isRegistered(name)) {
                server.registerMBean(new StandardMBean(metrics, ChannelWriteBufferMbean.class), name);
            }
            return name;
        } catch (JMException e) {
            throw new IllegalStateException("Failed to register channel write buffer MBean", e);
        }
    }

    public static void unregister(ObjectName name) {
        unregister(ManagementFactory.getPlatformMBeanServer(), name);
    }

    public static void unregister(MBeanServer server, ObjectName name) {
        try {
            if (server.isRegistered(name)) {
                server.unregisterMBean(name);
            }
        } catch (JMException e) {
            throw new IllegalStateException("Failed to unregister channel write buffer MBean", e);
        }
    }

    private ChannelWriteBufferMbeans() {
    }
}
