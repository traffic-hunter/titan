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

import java.lang.management.ManagementFactory;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.StandardMBean;

/**
 * Registers process-wide slow-consumer metrics with JMX.
 *
 * @author yun
 */
public final class SlowConsumerMetricsMbeans {

    public static final String OBJECT_NAME = "org.traffichunter.titan:type=SlowConsumer";

    private SlowConsumerMetricsMbeans() {
    }

    public static ObjectName objectName() {
        try {
            return new ObjectName(OBJECT_NAME);
        } catch (Exception e) {
            throw new IllegalStateException("Invalid slow consumer MBean name", e);
        }
    }

    static void register(SlowConsumerMetricsMbean metrics) {
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        ObjectName name = objectName();
        try {
            if (!server.isRegistered(name)) {
                server.registerMBean(new StandardMBean(metrics, SlowConsumerMetricsMbean.class), name);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to register slow consumer MBean", e);
        }
    }
}
