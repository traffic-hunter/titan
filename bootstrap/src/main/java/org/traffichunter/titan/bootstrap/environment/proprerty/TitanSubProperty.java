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
package org.traffichunter.titan.bootstrap.environment.proprerty;

import java.util.List;
import java.util.Objects;
import org.traffichunter.titan.bootstrap.environment.proprerty.sub.BackupProperty;
import org.traffichunter.titan.bootstrap.environment.proprerty.sub.FlowControlProperty;
import org.traffichunter.titan.bootstrap.environment.proprerty.sub.HttpServerProperty;
import org.traffichunter.titan.bootstrap.environment.proprerty.sub.MonitorProperty;
import org.traffichunter.titan.bootstrap.environment.proprerty.sub.ServerProperty;
import org.traffichunter.titan.bootstrap.environment.proprerty.sub.ServiceDiscoveryProperty;

/**
 * YAML binding object for properties nested below the {@code titan} key.
 *
 * <p>Some fields are placeholders for broader process features, while
 * {@link #servers} is the active path used to construct managed server
 * settings.</p>
 */
public class TitanSubProperty {

    private HttpServerProperty httpServer;

    private MonitorProperty monitor;

    private BackupProperty backup;

    private FlowControlProperty flowControl;

    private ServiceDiscoveryProperty serviceDiscovery;

    private List<ServerProperty> servers;

    public TitanSubProperty() {
    }

    public TitanSubProperty(
            HttpServerProperty httpServer,
            MonitorProperty monitor,
            BackupProperty backup,
            FlowControlProperty flowControl,
            ServiceDiscoveryProperty serviceDiscovery,
            List<ServerProperty> servers
    ) {
        this.httpServer = httpServer;
        this.monitor = monitor;
        this.backup = backup;
        this.flowControl = flowControl;
        this.serviceDiscovery = serviceDiscovery;
        this.servers = servers;
    }

    public HttpServerProperty getHttpServer() {
        return httpServer;
    }

    public void setHttpServer(HttpServerProperty httpServer) {
        this.httpServer = httpServer;
    }

    public MonitorProperty getMonitor() {
        return monitor;
    }

    public void setMonitor(MonitorProperty monitor) {
        this.monitor = monitor;
    }

    public BackupProperty getBackup() {
        return backup;
    }

    public void setBackup(BackupProperty backup) {
        this.backup = backup;
    }

    public FlowControlProperty getFlowControl() {
        return flowControl;
    }

    public void setFlowControl(FlowControlProperty flowControl) {
        this.flowControl = flowControl;
    }

    public ServiceDiscoveryProperty getServiceDiscovery() {
        return serviceDiscovery;
    }

    public void setServiceDiscovery(ServiceDiscoveryProperty serviceDiscovery) {
        this.serviceDiscovery = serviceDiscovery;
    }

    public List<ServerProperty> getServers() {
        return servers;
    }

    public void setServers(List<ServerProperty> servers) {
        this.servers = servers;
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj || obj instanceof TitanSubProperty other
                && Objects.equals(httpServer, other.httpServer)
                && Objects.equals(monitor, other.monitor)
                && Objects.equals(backup, other.backup)
                && Objects.equals(flowControl, other.flowControl)
                && Objects.equals(serviceDiscovery, other.serviceDiscovery)
                && Objects.equals(servers, other.servers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(httpServer, monitor, backup, flowControl, serviceDiscovery, servers);
    }

    @Override
    public String toString() {
        return "TitanSubProperty{" +
                "httpServer=" + httpServer +
                ", monitor=" + monitor +
                ", backup=" + backup +
                ", flowControl=" + flowControl +
                ", serviceDiscovery=" + serviceDiscovery +
                ", servers=" + servers +
                '}';
    }
}
