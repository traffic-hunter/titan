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

import org.jspecify.annotations.Nullable;

import javax.net.ssl.KeyManager;
import javax.net.ssl.TrustManager;
import java.nio.file.Path;

/**
 * Immutable configuration for creating a JDK TLS context. Key and trust material can be
 * supplied through a key store or as managers prepared by an external configuration system.
 *
 * @author yun
 */
public final class TlsOptions {

    private final TlsSide side;
    private final TlsVersion[] versions;
    private final String[] ciphers;
    private final TlsClientAuth clientAuth;
    private final @Nullable Path path;
    private final @Nullable String type;
    private final @Nullable String storePassword;
    private final @Nullable String keyPassword;
    private final KeyManager[] keyManagers;
    private final TrustManager[] trustManagers;
    private final boolean managed;
    private final boolean verifyHostname;

    private TlsOptions(Builder builder) {
        if (builder.side == null) {
            throw new IllegalStateException("TLS side is required");
        }
        if (!builder.managed && builder.path == null) {
            throw new IllegalStateException("TLS key store or managers are required");
        }
        if (builder.managed && builder.path != null) {
            throw new IllegalStateException("TLS key store and managers cannot be configured together");
        }

        this.side = builder.side;
        this.versions = builder.versions.clone();
        this.ciphers = builder.ciphers.clone();
        this.clientAuth = builder.clientAuth;
        this.path = builder.path;
        this.type = builder.type;
        this.storePassword = builder.storePassword;
        this.keyPassword = builder.keyPassword;
        this.keyManagers = builder.keyManagers.clone();
        this.trustManagers = builder.trustManagers.clone();
        this.managed = builder.managed;
        this.verifyHostname = builder.verifyHostname;
    }

    public static Builder builder() {
        return new Builder();
    }

    public TlsSide side() {
        return side;
    }

    public TlsVersion[] versions() {
        return versions.clone();
    }

    public String[] ciphers() {
        return ciphers.clone();
    }

    public TlsClientAuth clientAuth() {
        return clientAuth;
    }

    public @Nullable Path path() {
        return path;
    }

    public @Nullable String type() {
        return type;
    }

    public @Nullable String storePassword() {
        return storePassword;
    }

    public @Nullable String keyPassword() {
        return keyPassword;
    }

    public KeyManager[] keyManagers() {
        return keyManagers.clone();
    }

    public TrustManager[] trustManagers() {
        return trustManagers.clone();
    }

    public boolean usesManagers() {
        return managed;
    }

    public boolean verifyHostname() {
        return verifyHostname;
    }

    public static final class Builder {

        private @Nullable TlsSide side;
        private TlsVersion[] versions = TlsVersion.values();
        private String[] ciphers = new String[0];
        private TlsClientAuth clientAuth = TlsClientAuth.NONE;
        private @Nullable Path path;
        private @Nullable String type;
        private @Nullable String storePassword;
        private @Nullable String keyPassword;
        private KeyManager[] keyManagers = new KeyManager[0];
        private TrustManager[] trustManagers = new TrustManager[0];
        private boolean managed;
        private boolean verifyHostname;

        private Builder() {
        }

        public Builder side(TlsSide side) {
            this.side = side;
            return this;
        }

        public Builder versions(TlsVersion... versions) {
            this.versions = versions.clone();
            return this;
        }

        public Builder ciphers(String... ciphers) {
            this.ciphers = ciphers.clone();
            return this;
        }

        public Builder clientAuth(TlsClientAuth clientAuth) {
            this.clientAuth = clientAuth;
            return this;
        }

        public Builder keyStore(Path path, String type, String storePassword, String keyPassword) {
            this.path = path;
            this.type = type;
            this.storePassword = storePassword;
            this.keyPassword = keyPassword;
            return this;
        }

        public Builder managers(KeyManager[] keyManagers, TrustManager[] trustManagers) {
            this.keyManagers = keyManagers.clone();
            this.trustManagers = trustManagers.clone();
            this.managed = true;
            return this;
        }

        public Builder verifyHostname(boolean verifyHostname) {
            this.verifyHostname = verifyHostname;
            return this;
        }

        public TlsOptions build() {
            return new TlsOptions(this);
        }
    }
}
