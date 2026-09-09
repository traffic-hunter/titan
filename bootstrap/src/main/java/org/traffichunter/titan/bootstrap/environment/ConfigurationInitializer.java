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

import java.io.InputStream;
import org.traffichunter.titan.bootstrap.Settings;

/**
 * Loads external configuration into bootstrap {@link Settings}.
 *
 * <p>The default implementation reads Titan's YAML environment file, but tests
 * and alternate launchers can provide an input stream directly. Implementations
 * should return normalized settings rather than exposing parser-specific
 * objects to the rest of the runtime.</p>
 */
public interface ConfigurationInitializer {

    static ConfigurationInitializer getDefault(final String path) {
        return new YamlConfigurationInitializer(path);
    }

    Settings load();

    Settings load(InputStream is);
}
