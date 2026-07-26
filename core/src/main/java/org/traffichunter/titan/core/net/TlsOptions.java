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

import java.nio.file.Path;

/**
 * Configuration for creating a JDK TLS context.
 *
 * @param side local endpoint role during the TLS handshake
 * @param versions TLS protocol versions enabled on each engine
 * @param clientAuth server policy for requesting or requiring client certificates
 * @param path path to the key store containing identity and trust material
 * @param type JDK key store type, such as {@code PKCS12} or {@code JKS}
 * @param storePassword password used to open the key store
 * @param keyPassword password used to recover private keys from the key store
 * @param verifyHostname whether a client verifies the peer hostname against the certificate
 *
 * @author yun
 */
public record TlsOptions(
        TlsSide side,
        TlsVersion[] versions,
        TlsClientAuth clientAuth,
        Path path,
        String type,
        String storePassword,
        String keyPassword,
        boolean verifyHostname
) {

    public TlsOptions {
        versions = versions.clone();
    }

    @Override
    public TlsVersion[] versions() {
        return versions.clone();
    }
}
