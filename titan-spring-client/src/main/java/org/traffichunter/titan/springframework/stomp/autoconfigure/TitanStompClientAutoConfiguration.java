package org.traffichunter.titan.springframework.stomp.autoconfigure;

import java.time.Duration;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.ssl.SslAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.boot.ssl.SslManagerBundle;
import org.springframework.context.annotation.Bean;
import org.traffichunter.titan.client.StompEndpoint;
import org.traffichunter.titan.client.TitanClient;
import org.traffichunter.titan.core.channel.EventLoopGroups;
import org.traffichunter.titan.core.net.JdkTlsContext;
import org.traffichunter.titan.core.net.TlsOptions;
import org.traffichunter.titan.core.net.TlsSide;
import org.traffichunter.titan.core.net.TlsVersion;
import org.traffichunter.titan.springframework.stomp.core.TitanClientManager;
import org.traffichunter.titan.springframework.stomp.TitanProperties;
import org.traffichunter.titan.springframework.stomp.core.TitanTemplate;

/**
 * Autoconfiguration for Titan's Spring STOMP client integration.
 *
 * <p>Maps Spring properties to {@link TitanClient.Builder} without exposing a low-level STOMP
 * option bean. Native clients create the configured number of I/O workers; Vert.x manages
 * its own event loops. A user-defined {@link TitanClient} bean replaces the default client.</p>
 *
 * @author yun
 */
@AutoConfiguration(after = SslAutoConfiguration.class)
@ConditionalOnClass({TitanClient.class, EventLoopGroups.class})
@EnableConfigurationProperties(TitanProperties.class)
@ConditionalOnProperty(prefix = "spring.titan", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TitanStompClientAutoConfiguration {

    /**
     * Creates the configured client facade and applies endpoint-derived WebSocket and TLS settings.
     */
    @Bean
    @ConditionalOnMissingBean
    public TitanClient titanStompClient(
            TitanProperties properties,
            ObjectProvider<SslBundles> sslBundles
    ) {
        StompEndpoint endpoint = endpoint(properties);
        TitanClient.Builder builder = TitanClient.builder()
                .implementation(properties.getClient() == TitanProperties.Client.VERTX
                        ? TitanClient.Implementation.VERTX
                        : TitanClient.Implementation.TITAN)
                .worker(properties.getWorker())
                .host(endpoint == null ? properties.getHost() : endpoint.host())
                .port(endpoint == null ? properties.getPort() : endpoint.port())
                .session(properties.toStompSessionOption())
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMillis()))
                .tls(resolveTlsContext(properties, endpoint, sslBundles));

        boolean webSocket = endpoint == null
                ? properties.getTransport() == TitanProperties.Transport.WEBSOCKET
                : endpoint.isWebSocket();
        if (webSocket) {
            String path = endpoint == null ? properties.getWebsocketPath() : endpoint.path();
            builder.webSocket(path);
        }
        return builder.build();
    }

    @Bean
    @ConditionalOnMissingBean
    public TitanClientManager titanClientManager(
            TitanClient titanStompClient,
            TitanProperties properties
    ) {
        return new TitanClientManager(titanStompClient, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public TitanTemplate titanTemplate(TitanClientManager titanClientManager) {
        return new TitanTemplate(titanClientManager);
    }

    private static @Nullable JdkTlsContext resolveTlsContext(
            TitanProperties properties,
            @Nullable StompEndpoint endpoint,
            ObjectProvider<SslBundles> sslBundles
    ) {
        String bundleName = properties.getSsl().getBundle();
        boolean hasBundle = bundleName != null && !bundleName.isBlank();
        boolean secureEndpoint = endpoint != null && endpoint.isSecure();

        if (secureEndpoint && !hasBundle) {
            throw new IllegalStateException("Secure STOMP endpoint requires spring.titan.ssl.bundle");
        }
        if (!hasBundle) {
            return null;
        }
        if (endpoint != null && endpoint.isWebSocket() && !secureEndpoint) {
            throw new IllegalStateException("WebSocket SSL bundle requires a wss endpoint");
        }
        SslBundles bundles = sslBundles.getIfAvailable();
        if (bundles == null) {
            throw new IllegalStateException("Spring SSL bundle infrastructure is not available");
        }

        SslBundle bundle = bundles.getBundle(bundleName);
        SslManagerBundle managers = bundle.getManagers();
        String[] protocols = bundle.getOptions().getEnabledProtocols();
        String[] ciphers = bundle.getOptions().getCiphers();
        TlsOptions.Builder options = TlsOptions.builder()
                .side(TlsSide.CLIENT)
                .managers(managers.getKeyManagers(), managers.getTrustManagers())
                .verifyHostname(properties.getSsl().isVerifyHostname());
        if (protocols != null && protocols.length > 0) {
            options.versions(tlsVersions(protocols));
        }
        if (ciphers != null) {
            options.ciphers(ciphers);
        }
        return new JdkTlsContext(options.build());
    }

    private static TlsVersion[] tlsVersions(String[] protocols) {
        TlsVersion[] versions = new TlsVersion[protocols.length];
        for (int i = 0; i < protocols.length; i++) {
            String protocol = protocols[i];
            if (TlsVersion.TLS_1_2.getValue().equals(protocol)) {
                versions[i] = TlsVersion.TLS_1_2;
            } else if (TlsVersion.TLS_1_3.getValue().equals(protocol)) {
                versions[i] = TlsVersion.TLS_1_3;
            } else {
                throw new IllegalStateException("Unsupported TLS protocol in Spring SSL bundle: " + protocol);
            }
        }
        return versions;
    }

    private static @Nullable StompEndpoint endpoint(TitanProperties properties) {
        String value = properties.getEndpoint();
        return value == null || value.isBlank() ? null : StompEndpoint.parse(value);
    }
}
