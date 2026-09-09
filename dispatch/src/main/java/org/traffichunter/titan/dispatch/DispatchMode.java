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

import org.traffichunter.titan.dispatch.exporter.DispatchExporter;

/**
 * Execution strategy used by a dispatch gateway.
 *
 * <p>The mode intentionally selects only the threading model. Routing,
 * destination ownership, and exporter behavior remain the same for every mode,
 * so protocol launchers can switch execution strategy without changing the
 * fanout contract.</p>
 */
public enum DispatchMode {

    PLATFORM_EXECUTOR("platform") {
        @Override
        public DispatchGateway dispatchGateway(DispatchExporter dispatchExporter) {
            return DispatchGateway.ofThread(dispatchExporter);
        }

        @Override
        public DispatchGateway dispatchGateway(DispatchExporter dispatchExporter, Dispatcher dispatcher) {
            return DispatchGateway.ofThread(dispatchExporter, dispatcher);
        }
    },
    VT_EXECUTOR("virtual") {
        @Override
        public DispatchGateway dispatchGateway(DispatchExporter dispatchExporter) {
            return DispatchGateway.ofVirtual(dispatchExporter);
        }

        @Override
        public DispatchGateway dispatchGateway(DispatchExporter dispatchExporter, Dispatcher dispatcher) {
            return DispatchGateway.ofVirtual(dispatchExporter, dispatcher);
        }
    },
    ;

    private final String name;

    DispatchMode(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public abstract DispatchGateway dispatchGateway(DispatchExporter dispatchExporter);

    public abstract DispatchGateway dispatchGateway(DispatchExporter dispatchExporter, Dispatcher dispatcher);

    public static DispatchMode resolveMode(String modeName) {
        return switch (modeName) {
            case "platform" -> DispatchMode.PLATFORM_EXECUTOR;
            case "virtual" -> DispatchMode.VT_EXECUTOR;
            default -> throw new IllegalStateException("Unexpected value: " + modeName);
        };
    }
}
