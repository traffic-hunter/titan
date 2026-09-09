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

import org.traffichunter.titan.bootstrap.Banner.Mode;

/**
 * Reads process-level configuration from Java system properties.
 *
 * <p>These values are intentionally limited to bootstrap concerns such as the
 * environment file path, banner mode, and coarse default limits. Rich server
 * configuration is loaded from the YAML environment file into
 * {@link Settings}.</p>
 */
public final class Configurations {

    public static Banner.Mode banner(final Property property) {
        String mode = System.getProperty(property.value);
        if (mode == null || mode.isBlank()) {
            return Mode.ON;
        }

        if ("false".equalsIgnoreCase(mode) || "off".equalsIgnoreCase(mode)) {
            return Mode.OFF;
        }

        return Mode.ON;
    }

    public static int port(final Property property) {
        String port = System.getProperty(property.value);

        if(port == null || port.isEmpty()) {
            return 7777;
        }

        return Integer.parseInt(port);
    }

    public static String environment() {
        String environmentPath = System.getProperty(Property.ENVIRONMENT.value);

        if(environmentPath == null || environmentPath.isEmpty()) {
            return "./titan-env.yml";
        }

        return environmentPath;
    }

    public static Integer maxConnection() {
        String property = System.getProperty(Property.MAX_CONNECTION_COUNT.value);

        if(property == null || property.isEmpty()) {
            return 8192;
        }

        return Integer.parseInt(property);
    }

    public static String name() {
        String property = System.getProperty(Property.NAME.value);

        if(property == null || property.isEmpty()) {
            return "titan";
        }

        return property;
    }

    public static int taskPendingCapacity() {
        String property = System.getProperty(Property.EVENTLOOP_PENDING_MAX_CAPACITY.value);

        if(property == null || property.isEmpty()) {
            return 500;
        }

        return Integer.parseInt(property);
    }

    public enum Property {
        BANNER_MODE("titan.banner.mode"),
        PORT("titan.web.server.port"),
        TRANSPORT_PORT("titan.transport.server.port"),
        ENVIRONMENT("titan.environment.path"),
        EVENTLOOP_PENDING_MAX_CAPACITY("titan.eventloop.pending.capacity"),
        MAX_CONNECTION_COUNT("titan.connection.max"),
        NAME("titan.name"),
        ;

        private final String value;

        Property(final String value) {
            this.value = value;
        }
    }

    private Configurations() {}
}
