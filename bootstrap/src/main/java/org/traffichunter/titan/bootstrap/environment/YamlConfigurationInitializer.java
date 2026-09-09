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
package org.traffichunter.titan.bootstrap.environment;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.bootstrap.ServerSettings;
import org.traffichunter.titan.bootstrap.Settings;
import org.traffichunter.titan.bootstrap.environment.proprerty.RootYamlProperty;
import org.traffichunter.titan.bootstrap.environment.proprerty.sub.BackupProperty;
import org.traffichunter.titan.bootstrap.environment.proprerty.sub.FlowControlProperty;
import org.traffichunter.titan.bootstrap.environment.proprerty.sub.HeapFlowControlProperty;
import org.traffichunter.titan.bootstrap.environment.proprerty.sub.MonitorProperty;
import org.traffichunter.titan.bootstrap.environment.proprerty.sub.QueueFlowControlProperty;
import org.traffichunter.titan.bootstrap.environment.proprerty.sub.ServerProperty;
import org.traffichunter.titan.bootstrap.environment.proprerty.sub.TlsProperty;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

/**
 * SnakeYAML-backed environment loader.
 *
 * <p>The loader binds YAML into {@link RootYamlProperty} using
 * {@link RelaxedBindingUtils}, then maps server and process-wide configuration
 * into {@link Settings}. Runtime records apply defaults and validation during
 * this mapping step.</p>
 *
 * <pre>{@code
 * titan-env.yml
 *      |
 *      v
 * RootYamlProperty / TitanSubProperty / ServerProperty
 *      |
 *      v
 * ServerSettings
 *      |
 *      v
 * Settings
 * }</pre>
 */
final class YamlConfigurationInitializer implements ConfigurationInitializer {

    private static final Logger log = LoggerFactory.getLogger(YamlConfigurationInitializer.class);

    private static final String DEFAULT_ENV_FILE = "titan-env.yml";

    private final Yaml yaml;

    private final String path;

    public YamlConfigurationInitializer(final String path) {
        final Constructor constructor = new Constructor(RootYamlProperty.class, new LoaderOptions());

        constructor.setPropertyUtils(new RelaxedBindingUtils());

        this.yaml = new Yaml(constructor);
        this.path = path;
    }

    @Override
    public Settings load() {
        final InputStream is = getFile(path);

        RootYamlProperty root = yaml.load(is);

        return map(root);
    }

    @Override
    public Settings load(final InputStream is) {
        RootYamlProperty root = yaml.load(is);

        return map(root);
    }

    private static InputStream getFile(final String path) {
        try {
            return new FileInputStream(path);
        } catch (FileNotFoundException e) {
            log.error("Could not open file {} {}", path, e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private static Settings map(final RootYamlProperty root) {
        List<ServerSettings> servers = root.getTitan() == null || root.getTitan().getServers() == null
                ? List.of()
                : root.getTitan().getServers().stream()
                        .map(YamlConfigurationInitializer::mapServer)
                        .toList();

        return new Settings(
                servers,
                mapMonitor(root.getTitan() == null ? null : root.getTitan().getMonitor()),
                mapBackup(root.getTitan() == null ? null : root.getTitan().getBackup()),
                mapFlowControl(root.getTitan() == null ? null : root.getTitan().getFlowControl())
        );
    }

    private static Settings.FlowControlSettings mapFlowControl(
            final @Nullable FlowControlProperty property
    ) {
        if (property == null) {
            return Settings.FlowControlSettings.disabled();
        }

        HeapFlowControlProperty heap = property.getHeap();
        Settings.HeapFlowControlSettings heapSettings = heap == null
                ? Settings.HeapFlowControlSettings.defaults()
                : new Settings.HeapFlowControlSettings(
                        heap.isEnabled(),
                        heap.getHighWatermark(),
                        heap.getLowWatermark()
                );
        QueueFlowControlProperty queue = property.getQueue();
        Settings.QueueFlowControlSettings queueSettings = queue == null
                ? Settings.QueueFlowControlSettings.defaults()
                : new Settings.QueueFlowControlSettings(
                        queue.isEnabled(),
                        queue.getMaxPendingBytes(),
                        queue.getResumePendingBytes()
                );
        return new Settings.FlowControlSettings(property.isEnabled(), heapSettings, queueSettings);
    }

    private static Settings.BackupSettings mapBackup(final @Nullable BackupProperty property) {
        if (property == null) {
            return Settings.BackupSettings.disabled();
        }
        return Settings.BackupSettings.fromConfig(
                property.isEnabled(),
                property.getType(),
                property.getPath(),
                property.getSyncPolicy(),
                property.getRecoveryPolicy()
        );
    }

    private static Settings.MonitorSettings mapMonitor(final @Nullable MonitorProperty property) {
        if (property == null) {
            return Settings.MonitorSettings.disabled();
        }
        return new Settings.MonitorSettings(
                property.isEnabled(),
                property.getHost(),
                property.getPort(),
                property.getToken(),
                property.getThreadPoolSize()
        );
    }

    private static ServerSettings mapServer(final ServerProperty property) {
        return new ServerSettings(
                property.getName(),
                property.getTransport(),
                property.getProtocol(),
                property.getHost(),
                property.getPort(),
                property.getPrimaryThreads(),
                property.getSecondaryThreads(),
                property.getOptions(),
                property.getTransportOptions(),
                property.getProtocolOptions(),
                mapTls(property.getTls())
        );
    }

    private static ServerSettings.TlsSettings mapTls(final @Nullable TlsProperty property) {
        if (property == null) {
            return ServerSettings.TlsSettings.disabled();
        }

        return new ServerSettings.TlsSettings(
                true,
                property.getSide(),
                property.getClientAuth(),
                property.getPath(),
                property.getType(),
                property.getStorePassword(),
                property.getKeyPassword(),
                property.isVerifyHostname()
        );
    }
}
