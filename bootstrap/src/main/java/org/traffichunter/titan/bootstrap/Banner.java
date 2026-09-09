/*
 * Copyright 2024 traffic-hunter
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
package org.traffichunter.titan.bootstrap;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author yungwang-o
 */
public final class Banner {

    private static final String BANNER_NAME = "titan-banner.txt";
    private static final String DEFAULT_VERSION = "0.1.0";

    public void print(final Mode mode) {
        if(mode == Mode.OFF) {
            return;
        }

        try (final InputStream in = getClass().getClassLoader().getResourceAsStream(BANNER_NAME)) {

            BufferedReader reader = new BufferedReader(new InputStreamReader(Objects.requireNonNull(in)));
            String version = resolveVersion();

            String banner = reader.lines()
                    .map(line -> line
                            .replace("${version}", version)
                            .replace("${java.version}", System.getProperty("java.version"))
                            .replace("${java.specification}", System.getProperty("java.specification.version"))
                            .replace("${jdk}", System.getProperty("java.vendor"))
                            .replace("${time}", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))))
                    .collect(Collectors.joining(System.lineSeparator()));

            System.out.println(banner + "\n");

        } catch (IOException ignored) {}
    }

    private String resolveVersion() {
        Package bannerPackage = getClass().getPackage();
        if (bannerPackage != null && bannerPackage.getImplementationVersion() != null) {
            return bannerPackage.getImplementationVersion();
        }

        String version = System.getProperty("titan.version");
        if (version != null && !version.isBlank()) {
            return version;
        }

        return DEFAULT_VERSION;
    }

    public enum Mode {
        ON, OFF
    }
}
