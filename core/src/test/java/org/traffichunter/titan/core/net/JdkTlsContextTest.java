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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.traffichunter.titan.core.channel.WorkerEventLoopGroup;

import javax.net.ssl.SSLEngine;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author yun
 */
class JdkTlsContextTest {

    private static final String PASSWORD = "changeit";

    @TempDir
    Path directory;

    @Test
    void apply_client_tls_options_to_ssl_engine() throws Exception {
        Path keyStorePath = createKeyStore();
        TlsOptions options = new TlsOptions(
                TlsSide.CLIENT,
                new TlsVersion[]{TlsVersion.TLS_1_3, TlsVersion.TLS_1_2},
                TlsClientAuth.NONE,
                keyStorePath,
                "PKCS12",
                PASSWORD,
                PASSWORD,
                true
        );

        JdkTlsContext context = new JdkTlsContext(options);
        SSLEngine engine = context.newHandler(
                "localhost",
                61614,
                new WorkerEventLoopGroup(1)
        ).sslEngine();

        assertThat(engine.getUseClientMode()).isTrue();
        assertThat(engine.getEnabledProtocols()).containsExactly("TLSv1.3", "TLSv1.2");
        assertThat(engine.getSSLParameters().getEndpointIdentificationAlgorithm()).isEqualTo("HTTPS");
    }

    @Test
    void apply_server_client_auth_to_ssl_engine() throws Exception {
        TlsOptions options = new TlsOptions(
                TlsSide.SERVER,
                new TlsVersion[]{TlsVersion.TLS_1_3, TlsVersion.TLS_1_2},
                TlsClientAuth.NEED,
                createKeyStore(),
                "PKCS12",
                PASSWORD,
                PASSWORD,
                false
        );

        SSLEngine engine = new JdkTlsContext(options)
                .newHandler("localhost", 61614, new WorkerEventLoopGroup(1))
                .sslEngine();

        assertThat(engine.getUseClientMode()).isFalse();
        assertThat(engine.getEnabledProtocols()).containsExactly("TLSv1.3", "TLSv1.2");
        assertThat(engine.getNeedClientAuth()).isTrue();
        assertThat(engine.getSSLParameters().getEndpointIdentificationAlgorithm()).isNull();
    }

    @Test
    void protect_tls_version_array_from_external_changes() throws Exception {
        TlsVersion[] versions = {TlsVersion.TLS_1_3, TlsVersion.TLS_1_2};
        TlsOptions options = new TlsOptions(
                TlsSide.CLIENT,
                versions,
                TlsClientAuth.NONE,
                createKeyStore(),
                "PKCS12",
                PASSWORD,
                PASSWORD,
                true
        );

        versions[0] = TlsVersion.TLS_1_2;
        TlsVersion[] returned = options.versions();
        returned[0] = TlsVersion.TLS_1_2;

        assertThat(options.versions()).containsExactly(TlsVersion.TLS_1_3, TlsVersion.TLS_1_2);
    }

    @Test
    void reject_client_with_server_client_auth_policy() {
        TlsOptions options = new TlsOptions(
                TlsSide.CLIENT,
                new TlsVersion[]{TlsVersion.TLS_1_3, TlsVersion.TLS_1_2},
                TlsClientAuth.NEED,
                directory.resolve("unused.p12"),
                "PKCS12",
                PASSWORD,
                PASSWORD,
                true
        );

        assertThatThrownBy(() -> new JdkTlsContext(options))
                .isInstanceOf(NetSecureException.class)
                .hasMessageContaining("client authentication");
    }

    @Test
    void reject_empty_tls_versions() throws Exception {
        TlsOptions options = new TlsOptions(
                TlsSide.SERVER,
                new TlsVersion[0],
                TlsClientAuth.NONE,
                createKeyStore(),
                "PKCS12",
                PASSWORD,
                PASSWORD,
                false
        );

        assertThatThrownBy(() -> new JdkTlsContext(options))
                .isInstanceOf(NetSecureException.class)
                .hasMessageContaining("TLS version");
    }

    @Test
    void reject_invalid_key_store_password() throws Exception {
        TlsOptions options = new TlsOptions(
                TlsSide.SERVER,
                new TlsVersion[]{TlsVersion.TLS_1_3},
                TlsClientAuth.NONE,
                createKeyStore(),
                "PKCS12",
                "wrong-password",
                PASSWORD,
                false
        );

        assertThatThrownBy(() -> new JdkTlsContext(options))
                .isInstanceOf(NetSecureException.class)
                .hasMessageContaining("TLS key store");
    }

    private Path createKeyStore() throws Exception {
        Path path = directory.resolve("tls.p12");
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, PASSWORD.toCharArray());

        try (OutputStream output = Files.newOutputStream(path)) {
            keyStore.store(output, PASSWORD.toCharArray());
        }
        return path;
    }
}
