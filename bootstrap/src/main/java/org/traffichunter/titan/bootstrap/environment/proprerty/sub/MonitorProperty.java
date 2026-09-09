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

import java.util.Objects;

/**
 * YAML DTO for monitor scheduling options.
 */
public class MonitorProperty {

    private boolean enabled;

    private String host;

    private int port;

    private String token;

    private int threadPoolSize;

    private long initialDelay;

    private long delay;

    private int scheduledThreadPool;

    public MonitorProperty() {
    }

    public MonitorProperty(
            boolean enabled,
            String host,
            int port,
            String token,
            int threadPoolSize,
            long initialDelay,
            long delay,
            int scheduledThreadPool
    ) {
        this.enabled = enabled;
        this.host = host;
        this.port = port;
        this.token = token;
        this.threadPoolSize = threadPoolSize;
        this.initialDelay = initialDelay;
        this.delay = delay;
        this.scheduledThreadPool = scheduledThreadPool;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public int getThreadPoolSize() {
        return threadPoolSize;
    }

    public void setThreadPoolSize(int threadPoolSize) {
        this.threadPoolSize = threadPoolSize;
    }

    public long getInitialDelay() {
        return initialDelay;
    }

    public void setInitialDelay(long initialDelay) {
        this.initialDelay = initialDelay;
    }

    public long getDelay() {
        return delay;
    }

    public void setDelay(long delay) {
        this.delay = delay;
    }

    public int getScheduledThreadPool() {
        return scheduledThreadPool;
    }

    public void setScheduledThreadPool(int scheduledThreadPool) {
        this.scheduledThreadPool = scheduledThreadPool;
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj || obj instanceof MonitorProperty other
                && enabled == other.enabled
                && port == other.port
                && threadPoolSize == other.threadPoolSize
                && initialDelay == other.initialDelay
                && delay == other.delay
                && scheduledThreadPool == other.scheduledThreadPool
                && Objects.equals(host, other.host)
                && Objects.equals(token, other.token);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                enabled,
                host,
                port,
                token,
                threadPoolSize,
                initialDelay,
                delay,
                scheduledThreadPool
        );
    }

    @Override
    public String toString() {
        return "MonitorProperty{" +
                "enabled=" + enabled +
                ", host='" + host + '\'' +
                ", port=" + port +
                ", token='" + token + '\'' +
                ", threadPoolSize=" + threadPoolSize +
                ", initialDelay=" + initialDelay +
                ", delay=" + delay +
                ", scheduledThreadPool=" + scheduledThreadPool +
                '}';
    }
}
