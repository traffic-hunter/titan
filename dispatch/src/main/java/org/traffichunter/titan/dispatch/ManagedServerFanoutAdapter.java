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

import org.traffichunter.titan.core.spi.ManagedServer;
import org.traffichunter.titan.dispatch.exporter.DispatchExporter;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.function.Function;

/**
 * Adapter SPI for installing fanout behavior into a concrete managed server.
 */
public interface ManagedServerFanoutAdapter {

    static List<ManagedServerFanoutAdapter> load() {
        return ServiceLoader.load(ManagedServerFanoutAdapter.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .sorted(Comparator.comparingInt(ManagedServerFanoutAdapter::order))
                .toList();
    }

    default int order() {
        return 0;
    }

    boolean supports(
            String protocol,
            String transport,
            Map<String, String> protocolOptions,
            ManagedServer managedServer
    );

    void apply(
            String protocol,
            String transport,
            Map<String, String> protocolOptions,
            ManagedServer managedServer,
            Function<DispatchExporter, DispatchGateway> gatewayFactory
    );
}
