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
package org.traffichunter.titan.core.codec.stomp;

/**
 * @author yungwang-o
 */
public enum StompVersion {
    STOMP_1_2("stomp", "1.2"),
    STOMP_1_1("stomp", "1.1"),
    STOMP_1_0("stomp", "1.0"),
    ;

    private final String name;
    private final String version;

    StompVersion(final String name, final String version) {
        this.name = name;
        this.version = version;
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public void validate(final String version) {
        if(!this.version.equals(version)) {
            throw new StompException("Version " + version + " does not match expected version " + this.version);
        }
    }
}
