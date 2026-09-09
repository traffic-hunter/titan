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
package org.traffichunter.titan.core.net;

/**
 * @author yun
 */
public enum TlsVersion {

    TLS_1_2("TLSv1.2"),
    TLS_1_3("TLSv1.3"),
    ;

    private final String value;

    TlsVersion(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static String[] values(TlsVersion[] versions) {
        String[] result = new String[versions.length];
        for (int i = 0; i < versions.length; i++) {
            TlsVersion version = versions[i];
            result[i] = version.getValue();
        }

        return result;
    }
}
