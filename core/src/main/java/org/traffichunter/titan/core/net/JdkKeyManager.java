/*
The MIT License

Copyright (c) 2025 traffic-hunter

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
*/
package org.traffichunter.titan.core.net;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
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
        this.keyPassword = options.keyPassword();
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
        try {
            KeyStore keyStore = KeyStore.getInstance(options.type());
            try (InputStream input = Files.newInputStream(options.path())) {
                keyStore.load(input, options.storePassword().toCharArray());
            }
            return keyStore;
        } catch (GeneralSecurityException | IOException e) {
            throw new NetSecureException("Failed to load TLS key store: " + options.path(), e);
        }
    }
}
