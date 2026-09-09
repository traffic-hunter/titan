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

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

/**
 * @author yun
 */
public final class JdkKeyManager {

    private final KeyStore keyStore;
    private final String keyPassword;

    public JdkKeyManager(TlsOptions options) {
        this.keyStore = load(options);
        String configuredKeyPassword = options.keyPassword();
        if (configuredKeyPassword == null) {
            throw new NetSecureException("TLS key password is required");
        }
        this.keyPassword = configuredKeyPassword;
    }

    public KeyManager[] keyManagers() {
        try {
            KeyManagerFactory factory = KeyManagerFactory.getInstance(
                    KeyManagerFactory.getDefaultAlgorithm()
            );
            factory.init(keyStore, keyPassword.toCharArray());
            return factory.getKeyManagers();
        } catch (GeneralSecurityException e) {
            throw new NetSecureException("Failed to initialize TLS key managers", e);
        }
    }

    public TrustManager[] trustManagers() {
        try {
            TrustManagerFactory factory = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm()
            );
            factory.init(keyStore);
            return factory.getTrustManagers();
        } catch (GeneralSecurityException e) {
            throw new NetSecureException("Failed to initialize TLS trust managers", e);
        }
    }

    private static KeyStore load(TlsOptions options) {
        Path path = options.path();
        String type = options.type();
        String storePassword = options.storePassword();
        if (path == null || type == null || storePassword == null) {
            throw new NetSecureException("Incomplete TLS key store configuration");
        }

        try {
            KeyStore keyStore = KeyStore.getInstance(type);
            try (InputStream input = Files.newInputStream(path)) {
                keyStore.load(input, storePassword.toCharArray());
            }
            return keyStore;
        } catch (GeneralSecurityException | IOException e) {
            throw new NetSecureException("Failed to load TLS key store: " + path, e);
        }
    }
}
