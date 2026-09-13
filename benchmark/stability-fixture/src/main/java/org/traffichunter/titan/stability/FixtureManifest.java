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
package org.traffichunter.titan.stability;

import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.List;

import org.traffichunter.titan.core.codec.json.Json;

/**
 * What a fixture is actually running, written next to the results of the runs it served.
 *
 * <p>{@code queueLimitsApplied} is recorded on purpose. The shipped configuration only applies the
 * queue byte limits when the top-level flow-control switch is on, and it is off by default, so a
 * limit printed by a configuration file is not evidence that any limit was in force. The fixture
 * passes its limits straight to the dispatcher, and this field says so.</p>
 *
 * @author yun
 */
public record FixtureManifest(
        String host,
        int port,
        String transport,
        String webSocketPath,
        String path,
        String dispatchMode,
        int ioWorkers,
        int maxFrameLength,
        long queueMaxPendingBytes,
        long queueResumePendingBytes,
        boolean queueLimitsApplied,
        String startedAt,
        String titanVersion,
        String javaVersion,
        String javaVendor,
        String jvmName,
        String osName,
        String osVersion,
        String osArch,
        int availableProcessors,
        long maxHeapBytes,
        List<String> jvmArguments
) {

    public static FixtureManifest of(StabilityFixtureOptions options, int port) {
        boolean dispatchPath = options.path() == StabilityFixtureOptions.DeliveryPath.DISPATCH;
        Package titan = StabilityFixture.class.getPackage();
        String version = titan.getImplementationVersion();

        return new FixtureManifest(
                options.host(),
                port,
                options.transport().label(),
                options.transport() == StabilityFixtureOptions.Transport.WEBSOCKET ? options.webSocketPath() : "",
                options.path().label(),
                dispatchPath ? options.dispatchMode().label() : "",
                options.ioWorkers(),
                options.maxFrameLength(),
                dispatchPath ? options.queueMaxPendingBytes() : 0,
                dispatchPath ? options.queueResumePendingBytes() : 0,
                dispatchPath,
                Instant.now().toString(),
                version == null ? "unknown" : version,
                System.getProperty("java.version", "unknown"),
                System.getProperty("java.vendor", "unknown"),
                System.getProperty("java.vm.name", "unknown"),
                System.getProperty("os.name", "unknown"),
                System.getProperty("os.version", "unknown"),
                System.getProperty("os.arch", "unknown"),
                Runtime.getRuntime().availableProcessors(),
                Runtime.getRuntime().maxMemory(),
                List.copyOf(ManagementFactory.getRuntimeMXBean().getInputArguments())
        );
    }

    public String toJson() {
        return Json.serialize(this);
    }
}
