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
