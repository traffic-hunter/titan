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
package org.traffichunter.titan.smoke.titan;

/**
 * Transport selected for a packaged-server smoke scenario.
 *
 * @author yun
 */
enum TitanSmokeTransport {
    TCP("tcp"),
    WEBSOCKET("websocket");

    private final String setting;

    TitanSmokeTransport(String setting) {
        this.setting = setting;
    }

    String setting() {
        return setting;
    }

    boolean isWebSocket() {
        return this == WEBSOCKET;
    }
}
