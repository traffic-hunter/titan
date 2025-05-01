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
package org.traffichunter.titan.bootstrap.environment;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import lombok.extern.slf4j.Slf4j;
import org.traffichunter.titan.bootstrap.Settings;
import org.traffichunter.titan.bootstrap.environment.proprerty.RootYamlProperty;
import org.traffichunter.titan.bootstrap.servicediscovery.SettingsServiceDiscovery;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

/**
 * @author yungwang-o
 */
@Slf4j
final class YamlConfigurationInitializer implements ConfigurationInitializer {

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

        RootYamlProperty root =yaml.load(is);

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
        return Settings.builder()
                .serviceDiscovery(new SettingsServiceDiscovery(root.getTitan().getServiceDiscovery().getStruct()))
                .build();
    }
}
