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
package org.traffichunter.titan.core.util;

/**
 * @author yungwang-o
 */
public enum Protocol {
    STOMP("stomp", "1.2", "v12.stomp"),
    MQTT("mqtt", "5.0", "v50.mqtt"),
    ;

    private final String name;
    private final String version;
    private final String subProtocol;

    Protocol(final String name, final String version, final String subProtocol) {
        this.name = name;
        this.version = version;
        this.subProtocol = subProtocol;
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public String getSubProtocol() {
        return subProtocol;
    }

    public static Protocol subProtocol(String subProtocol) {
        for (Protocol protocol : values()) {
            if (protocol.subProtocol.equalsIgnoreCase(subProtocol) || protocol.name.equalsIgnoreCase(subProtocol)) {
                return protocol;
            }
        }

        throw new IllegalArgumentException("Unknown sub protocol: " + subProtocol);
    }
}
