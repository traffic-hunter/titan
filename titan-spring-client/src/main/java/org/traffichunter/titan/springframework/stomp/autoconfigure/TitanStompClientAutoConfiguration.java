package org.traffichunter.titan.springframework.stomp.autoconfigure;

import java.time.Duration;
import java.util.List;

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
import org.traffichunter.titan.core.channel.EventLoopGroups;
import org.traffichunter.titan.core.net.JdkTlsContext;
import org.traffichunter.titan.core.net.TlsOptions;
import org.traffichunter.titan.core.net.TlsSide;
import org.traffichunter.titan.core.net.TlsVersion;
import org.traffichunter.titan.core.transport.stomp.TitanStompClient;
import org.traffichunter.titan.core.transport.stomp.StompEndpoint;
import org.traffichunter.titan.core.transport.stomp.VertxStompClient;
import org.traffichunter.titan.core.transport.stomp.client.StompClient;
import org.traffichunter.titan.core.transport.stomp.client.StompClientProvider;
import org.traffichunter.titan.core.transport.stomp.client.TitanStompClientProvider;
import org.traffichunter.titan.core.transport.stomp.client.VertxStompClientProvider;
import org.traffichunter.titan.core.transport.stomp.option.StompClientOption;
import org.traffichunter.titan.springframework.stomp.core.TitanClientManager;
import org.traffichunter.titan.springframework.stomp.TitanProperties;
import org.traffichunter.titan.springframework.stomp.core.TitanTemplate;

/**
 * Autoconfiguration for Titan's Spring STOMP client integration.
 * Creates the client event loops, options, manager, and template.
 * User-defined beans can override each default component.
 *
 * @author yun
 */
@AutoConfiguration(after = SslAutoConfiguration.class)
@ConditionalOnClass({StompClient.class, EventLoopGroups.class})
@EnableConfigurationProperties(TitanProperties.class)
@ConditionalOnProperty(prefix = "spring.titan", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TitanStompClientAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "titanStompClientEventLoopGroups")
    @ConditionalOnProperty(prefix = "spring.titan", name = "client", havingValue = "titan", matchIfMissing = true)
    public EventLoopGroups titanStompClientEventLoopGroups(TitanProperties properties) {
        return EventLoopGroups.group(properties.getPrimaryThreads(), properties.getSecondaryThreads());
    }

    @Bean
    @ConditionalOnMissingBean
    public StompClientOption titanStompClientOption(TitanProperties properties) {
        StompEndpoint endpoint = endpoint(properties);
        return StompClientOption.builder()
                .host(endpoint == null ? properties.getHost() : endpoint.host())
                .port(endpoint == null ? properties.getPort() : endpoint.port())
                .login(properties.getLogin())
                .passcode(properties.getPasscode())
                .virtualHost(properties.getVirtualHost())
                .heartbeatX(properties.getHeartbeatX())
                .heartbeatY(properties.getHeartbeatY())
                .maxFrameLength(properties.getMaxFrameLength())
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMillis()))
                .autoComputeContentLength(properties.isAutoComputeContentLength())
                .useStompFrame(properties.isUseStompFrame())
                .bypassHostHeader(properties.isBypassHostHeader())
                .build();
    }

    @Bean
    @ConditionalOnMissingBean(name = "titanStompClientProvider")
    public StompClientProvider titanStompClientProvider(ObjectProvider<EventLoopGroups> titanStompClientEventLoopGroups) {
        return new TitanStompClientProvider(titanStompClientEventLoopGroups::getObject);
    }

    @Bean
    @ConditionalOnClass(VertxStompClient.class)
    @ConditionalOnMissingBean(name = "vertxStompClientProvider")
    public StompClientProvider vertxStompClientProvider() {
        return new VertxStompClientProvider();
    }

    @Bean
    @ConditionalOnMissingBean
    public StompClient titanStompClient(
            List<StompClientProvider> stompClientProviders,
            StompClientOption titanStompClientOption,
            TitanProperties properties,
            ObjectProvider<SslBundles> sslBundles
    ) {
        StompClient client = stompClientProviders.stream()
                .filter(provider -> provider.supports(properties.getClient().getName(), titanStompClientOption.stompVersion().getVersion()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No STOMP client provider found for client: "
                        + properties.getClient().getName()
                        + ", version: "
                        + titanStompClientOption.stompVersion().getVersion()))
                .create(titanStompClientOption);

        StompEndpoint endpoint = endpoint(properties);
        configureSsl(client, properties, endpoint, sslBundles);

        boolean webSocket = endpoint == null
                ? properties.getTransport() == TitanProperties.Transport.WEBSOCKET
                : endpoint.isWebSocket();
        if (webSocket) {
            String path = endpoint == null ? properties.getWebsocketPath() : endpoint.path();
            if (client instanceof TitanStompClient titanClient) {
                titanClient.upgradeWebsocket(path);
            } else if (client instanceof VertxStompClient vertxClient) {
                vertxClient.upgradeWebsocket(path);
            } else {
                throw new IllegalStateException("The selected STOMP client does not support WebSocket transport");
            }
        }
        return client;
    }

    private static void configureSsl(
            StompClient client,
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
            return;
        }
        if (endpoint != null && endpoint.isWebSocket() && !secureEndpoint) {
            throw new IllegalStateException("WebSocket SSL bundle requires a wss endpoint");
        }
        if (!(client instanceof TitanStompClient titanClient)) {
            throw new IllegalStateException("Spring SSL bundles are supported only by the Titan STOMP client");
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
        titanClient.tls(new JdkTlsContext(options.build()));
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

    @Bean
    @ConditionalOnMissingBean
    public TitanClientManager titanClientManager(
            StompClient titanStompClient,
            TitanProperties properties
    ) {
        return new TitanClientManager(titanStompClient, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public TitanTemplate titanTemplate(TitanClientManager titanClientManager) {
        return new TitanTemplate(titanClientManager);
    }
}
