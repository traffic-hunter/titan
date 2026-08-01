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

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.KeyManager;
import javax.net.ssl.TrustManager;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

/**
 * @author yun
 */
public final class JdkTlsContext implements TlsContext {

    private final SSLContext sslContext;
    private final TlsSide side;
    private final String[] enabledProtocols;
    private final String[] enabledCiphers;
    private final TlsClientAuth clientAuth;
    private final boolean verifyHostname;

    public JdkTlsContext(TlsOptions options) {
        this(
                createSslContext(options),
                options.side(),
                TlsVersion.values(options.versions()),
                options.ciphers(),
                options.clientAuth(),
                options.verifyHostname()
        );
    }

    private JdkTlsContext(
            SSLContext sslContext,
            TlsSide side,
            String[] enabledProtocols,
            String[] enabledCiphers,
            TlsClientAuth clientAuth,
            boolean verifyHostname
    ) {
        if (enabledProtocols.length == 0) {
            throw new NetSecureException("At least one TLS version is required");
        }
        if (side == TlsSide.CLIENT && clientAuth != TlsClientAuth.NONE) {
            throw new NetSecureException("TLS client cannot configure server-side client authentication");
        }

        this.sslContext = sslContext;
        this.side = side;
        this.enabledProtocols = enabledProtocols.clone();
        this.enabledCiphers = enabledCiphers.clone();
        this.clientAuth = clientAuth;
        this.verifyHostname = verifyHostname;
    }

    @Override
    public TlsHandler newHandler(String peerHost, int peerPort) {
        return newHandler(peerHost, peerPort, TlsTaskExecutor.immediate());
    }

    @Override
    public TlsHandler newHandler(String peerHost, int peerPort, TlsTaskExecutor taskExecutor) {
        SSLEngine engine = sslContext.createSSLEngine(peerHost, peerPort);

        boolean isClient = side == TlsSide.CLIENT;
        engine.setUseClientMode(isClient);
        engine.setEnabledProtocols(enabledProtocols);
        if (enabledCiphers.length > 0) {
            engine.setEnabledCipherSuites(enabledCiphers);
        }

        SSLParameters sslParameters = engine.getSSLParameters();

        if (isClient && verifyHostname) {
            sslParameters.setEndpointIdentificationAlgorithm("HTTPS");
        }

        // server
        if (!isClient) {
            switch (clientAuth) {
                case NONE -> {
                    sslParameters.setWantClientAuth(false);
                    sslParameters.setNeedClientAuth(false);
                }
                case WANT -> sslParameters.setWantClientAuth(true);
                case NEED -> sslParameters.setNeedClientAuth(true);
            }
        }

        engine.setSSLParameters(sslParameters);
        return new JdkTlsHandler(engine, taskExecutor);
    }

    @Override
    public TlsSide side() {
        return side;
    }

    @Override
    public SSLContext sslContext() {
        return sslContext;
    }

    private static SSLContext createSslContext(TlsOptions options) {
        validate(options);

        try {
            KeyManager[] keyManagers;
            TrustManager[] trustManagers;
            if (options.usesManagers()) {
                keyManagers = options.keyManagers();
                trustManagers = options.trustManagers();
            } else {
                JdkKeyManager manager = new JdkKeyManager(options);
                keyManagers = manager.keyManagers();
                trustManagers = manager.trustManagers();
            }

            SSLContext context = SSLContext.getInstance("TLS");
            context.init(keyManagers, trustManagers, new SecureRandom());
            return context;
        } catch (GeneralSecurityException e) {
            throw new NetSecureException("Failed to initialize TLS context", e);
        }
    }

    private static void validate(TlsOptions options) {
        if (options.versions().length == 0) {
            throw new NetSecureException("At least one TLS version is required");
        }

        if (options.side() == TlsSide.CLIENT && options.clientAuth() != TlsClientAuth.NONE) {
            throw new NetSecureException("TLS client cannot configure server-side client authentication");
        }
    }
}
