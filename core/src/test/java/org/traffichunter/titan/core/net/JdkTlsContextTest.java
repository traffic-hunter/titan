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
        TlsOptions options = keyStoreOptions(TlsSide.CLIENT, keyStorePath)
                .verifyHostname(true)
                .build();

        JdkTlsContext context = new JdkTlsContext(options);
        SSLEngine engine = context.newHandler("localhost", 61614).sslEngine();

        assertThat(engine.getUseClientMode()).isTrue();
        assertThat(engine.getEnabledProtocols()).containsExactly("TLSv1.3", "TLSv1.2");
        assertThat(engine.getSSLParameters().getEndpointIdentificationAlgorithm()).isEqualTo("HTTPS");
    }

    @Test
    void apply_server_client_auth_to_ssl_engine() throws Exception {
        TlsOptions options = keyStoreOptions(TlsSide.SERVER, createKeyStore())
                .clientAuth(TlsClientAuth.NEED)
                .build();

        SSLEngine engine = new JdkTlsContext(options)
                .newHandler("localhost", 61614)
                .sslEngine();

        assertThat(engine.getUseClientMode()).isFalse();
        assertThat(engine.getEnabledProtocols()).containsExactly("TLSv1.3", "TLSv1.2");
        assertThat(engine.getNeedClientAuth()).isTrue();
        assertThat(engine.getSSLParameters().getEndpointIdentificationAlgorithm()).isNull();
    }

    @Test
    void create_client_context_from_external_managers() {
        TlsOptions options = TlsOptions.builder()
                .side(TlsSide.CLIENT)
                .managers(new javax.net.ssl.KeyManager[0], new javax.net.ssl.TrustManager[0])
                .verifyHostname(true)
                .build();
        JdkTlsContext context = new JdkTlsContext(options);

        SSLEngine engine = context.newHandler("localhost", 61614).sslEngine();

        assertThat(context.side()).isEqualTo(TlsSide.CLIENT);
        assertThat(engine.getUseClientMode()).isTrue();
        assertThat(engine.getEnabledProtocols()).containsExactly("TLSv1.2", "TLSv1.3");
        assertThat(engine.getSSLParameters().getEndpointIdentificationAlgorithm()).isEqualTo("HTTPS");
    }

    @Test
    void protect_tls_version_array_from_external_changes() throws Exception {
        TlsVersion[] versions = {TlsVersion.TLS_1_3, TlsVersion.TLS_1_2};
        TlsOptions options = keyStoreOptions(TlsSide.CLIENT, createKeyStore())
                .versions(versions)
                .verifyHostname(true)
                .build();

        versions[0] = TlsVersion.TLS_1_2;
        TlsVersion[] returned = options.versions();
        returned[0] = TlsVersion.TLS_1_2;

        assertThat(options.versions()).containsExactly(TlsVersion.TLS_1_3, TlsVersion.TLS_1_2);
    }

    @Test
    void reject_client_with_server_client_auth_policy() {
        TlsOptions options = keyStoreOptions(TlsSide.CLIENT, directory.resolve("unused.p12"))
                .clientAuth(TlsClientAuth.NEED)
                .verifyHostname(true)
                .build();

        assertThatThrownBy(() -> new JdkTlsContext(options))
                .isInstanceOf(NetSecureException.class)
                .hasMessageContaining("client authentication");
    }

    @Test
    void reject_empty_tls_versions() throws Exception {
        TlsOptions options = keyStoreOptions(TlsSide.SERVER, createKeyStore())
                .versions()
                .build();

        assertThatThrownBy(() -> new JdkTlsContext(options))
                .isInstanceOf(NetSecureException.class)
                .hasMessageContaining("TLS version");
    }

    @Test
    void reject_invalid_key_store_password() throws Exception {
        TlsOptions options = TlsOptions.builder()
                .side(TlsSide.SERVER)
                .versions(TlsVersion.TLS_1_3)
                .keyStore(createKeyStore(), "PKCS12", "wrong-password", PASSWORD)
                .build();

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

    private static TlsOptions.Builder keyStoreOptions(TlsSide side, Path path) {
        return TlsOptions.builder()
                .side(side)
                .versions(TlsVersion.TLS_1_3, TlsVersion.TLS_1_2)
                .keyStore(path, "PKCS12", PASSWORD, PASSWORD);
    }
}
