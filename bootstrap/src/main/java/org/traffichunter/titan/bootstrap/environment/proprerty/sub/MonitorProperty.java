/*
 * The MIT License
 *
 * Copyright (c) 2025 traffic-hunter
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
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
