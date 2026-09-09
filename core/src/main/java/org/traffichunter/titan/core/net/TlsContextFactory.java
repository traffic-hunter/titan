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

import org.traffichunter.titan.bootstrap.ServerSettings;

import java.nio.file.Path;
import java.util.Locale;

/**
 * @author yun
 */
public final class TlsContextFactory {

    public static TlsContext create(ServerSettings.TlsSettings settings) {
        TlsOptions options = TlsOptions.builder()
                .side(TlsSide.valueOf(settings.side().toUpperCase(Locale.ROOT)))
                .versions(TlsVersion.values())
                .clientAuth(TlsClientAuth.valueOf(settings.clientAuth().toUpperCase(Locale.ROOT)))
                .keyStore(
                        Path.of(settings.path()),
                        settings.type(),
                        settings.storePassword(),
                        settings.keyPassword()
                )
                .verifyHostname(settings.verifyHostname())
                .build();

        return new JdkTlsContext(options);
    }
}
