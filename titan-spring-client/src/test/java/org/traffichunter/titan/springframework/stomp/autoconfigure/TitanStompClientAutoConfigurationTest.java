package org.traffichunter.titan.springframework.stomp.autoconfigure;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.ssl.SslAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.boot.ssl.SslManagerBundle;
import org.springframework.boot.ssl.SslOptions;
import org.traffichunter.titan.client.TitanClient;
import org.traffichunter.titan.springframework.stomp.core.TitanClientManager;
import org.traffichunter.titan.springframework.stomp.TitanProperties;
import org.traffichunter.titan.springframework.stomp.core.TitanTemplate;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.net.ssl.KeyManager;
import javax.net.ssl.TrustManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TitanStompClientAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    SslAutoConfiguration.class,
                    TitanStompClientAutoConfiguration.class
            ));

    @TempDir
    Path directory;

    @Test
    void creates_titan_client_from_titan_client() {
        TitanStompClientAutoConfiguration configuration = new TitanStompClientAutoConfiguration();
        TitanProperties properties = new TitanProperties();
        properties.setClient(TitanProperties.Client.TITAN);

        TitanClient client = configuration.titanStompClient(
                properties,
                noSslBundles()
        );

        assertThat(client.name()).isEqualTo("titan");
    }

    @Test
    void creates_vertx_client_from_vertx_client() {
        TitanStompClientAutoConfiguration configuration = new TitanStompClientAutoConfiguration();
        TitanProperties properties = new TitanProperties();
        properties.setClient(TitanProperties.Client.VERTX);

        TitanClient client = configuration.titanStompClient(
                properties,
                noSslBundles()
        );

        assertThat(client.name()).isEqualTo("vertx");
    }

    @Test
    void configures_websocket_transport_for_titan_client() {
        TitanStompClientAutoConfiguration configuration = new TitanStompClientAutoConfiguration();
        TitanProperties properties = new TitanProperties();
        properties.setTransport(TitanProperties.Transport.WEBSOCKET);
        properties.setWebsocketPath("/titan");

        TitanClient client = configuration.titanStompClient(
                properties,
                noSslBundles()
        );

        assertThat(client.name()).isEqualTo("titan");
    }

    @Test
    void configures_titan_client_from_websocket_endpoint() {
        TitanStompClientAutoConfiguration configuration = new TitanStompClientAutoConfiguration();
        TitanProperties properties = new TitanProperties();
        properties.setEndpoint("ws://localhost:8080/titan");
        TitanClient client = configuration.titanStompClient(
                properties,
                noSslBundles()
        );

        assertThat(client.name()).isEqualTo("titan");
    }

    @Test
    void configures_secure_websocket_from_spring_ssl_bundle() throws Exception {
        TitanStompClientAutoConfiguration configuration = new TitanStompClientAutoConfiguration();
        TitanProperties properties = new TitanProperties();
        properties.setEndpoint("wss://localhost/stomp");
        properties.getSsl().setBundle("titan-client");

        SslOptions sslOptions = mock(SslOptions.class);
        when(sslOptions.getEnabledProtocols()).thenReturn(new String[0]);
        when(sslOptions.getCiphers()).thenReturn(new String[0]);
        SslBundle sslBundle = mock(SslBundle.class);
        when(sslBundle.getOptions()).thenReturn(sslOptions);
        SslManagerBundle managerBundle = mock(SslManagerBundle.class);
        when(managerBundle.getKeyManagers()).thenReturn(new KeyManager[0]);
        when(managerBundle.getTrustManagers()).thenReturn(new TrustManager[0]);
        when(sslBundle.getManagers()).thenReturn(managerBundle);

        TitanClient client = configuration.titanStompClient(
                properties,
                sslBundles(sslBundle)
        );

        assertThat(client.name()).isEqualTo("titan");
    }

    @Test
    void rejects_secure_websocket_without_ssl_bundle() {
        TitanStompClientAutoConfiguration configuration = new TitanStompClientAutoConfiguration();
        TitanProperties properties = new TitanProperties();
        properties.setEndpoint("wss://localhost/stomp");

        assertThatThrownBy(() -> configuration.titanStompClient(
                properties,
                noSslBundles()
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Secure STOMP endpoint requires spring.titan.ssl.bundle");
    }

    @Test
    void configures_websocket_transport_for_vertx_client() {
        TitanStompClientAutoConfiguration configuration = new TitanStompClientAutoConfiguration();
        TitanProperties properties = new TitanProperties();
        properties.setClient(TitanProperties.Client.VERTX);
        properties.setTransport(TitanProperties.Transport.WEBSOCKET);
        properties.setWebsocketPath("/titan");
        TitanClient client = configuration.titanStompClient(
                properties,
                noSslBundles()
        );

        assertThat(client.name()).isEqualTo("vertx");
    }

    @Test
    void lifecycle_starts_and_connects_client_when_auto_start_and_auto_connect_are_enabled() {
        TitanClient client = lifecycleClient();

        contextRunner
                .withBean(TitanClient.class, () -> client)
                .run(context -> {
                    assertThat(context).hasSingleBean(TitanClientManager.class);
                    assertThat(context.getBean(TitanClientManager.class).isRunning()).isTrue();
                    verify(client).start();
                    verify(client).connect();
                });
    }

    @Test
    void lifecycle_only_starts_client_when_auto_connect_is_disabled() {
        TitanClient client = lifecycleClient();

        contextRunner
                .withBean(TitanClient.class, () -> client)
                .withPropertyValues("spring.titan.auto-connect=false")
                .run(context -> {
                    assertThat(context.getBean(TitanClientManager.class).isRunning()).isTrue();
                    verify(client).start();
                    verify(client, never()).connect();
                });
    }

    @Test
    void lifecycle_does_not_start_client_when_auto_start_is_disabled() {
        TitanClient client = lifecycleClient();

        contextRunner
                .withBean(TitanClient.class, () -> client)
                .withPropertyValues("spring.titan.auto-start=false")
                .run(context -> {
                    assertThat(context.getBean(TitanClientManager.class).isRunning()).isFalse();
                    verify(client, never()).start();
                    verify(client, never()).connect();
                });
    }

    @Test
    void exposes_client_manager_and_template_with_public_bean_names() {
        TitanClient client = lifecycleClient();

        contextRunner
                .withBean(TitanClient.class, () -> client)
                .withPropertyValues("spring.titan.auto-start=false")
                .run(context -> {
                    assertThat(context).hasBean("titanClientManager");
                    assertThat(context.getBean("titanClientManager"))
                            .isInstanceOf(TitanClientManager.class);
                    assertThat(context).hasBean("titanTemplate");
                    assertThat(context.getBean("titanTemplate"))
                            .isInstanceOf(TitanTemplate.class);
                });
    }

    @Test
    void binds_client_property_to_client_enum() {
        TitanClient client = lifecycleClient();

        contextRunner
                .withBean(TitanClient.class, () -> client)
                .withPropertyValues(
                        "spring.titan.auto-start=false",
                        "spring.titan.client=vertx"
                )
                .run(context -> {
                    TitanProperties properties = context.getBean(TitanProperties.class);

                    assertThat(properties.getClient()).isEqualTo(TitanProperties.Client.VERTX);
                });
    }

    @Test
    void binds_websocket_transport_properties() {
        TitanClient client = lifecycleClient();

        contextRunner
                .withBean(TitanClient.class, () -> client)
                .withPropertyValues(
                        "spring.titan.auto-start=false",
                        "spring.titan.transport=websocket",
                        "spring.titan.websocket-path=/titan"
                )
                .run(context -> {
                    TitanProperties properties = context.getBean(TitanProperties.class);

                    assertThat(properties.getTransport()).isEqualTo(TitanProperties.Transport.WEBSOCKET);
                    assertThat(properties.getWebsocketPath()).isEqualTo("/titan");
                });
    }

    @Test
    void binds_endpoint_property() {
        TitanClient client = lifecycleClient();

        contextRunner
                .withBean(TitanClient.class, () -> client)
                .withPropertyValues(
                        "spring.titan.auto-start=false",
                        "spring.titan.endpoint=ws://localhost:8080/stomp"
                )
                .run(context -> assertThat(context.getBean(TitanProperties.class).getEndpoint())
                        .isEqualTo("ws://localhost:8080/stomp"));
    }

    @Test
    void binds_spring_ssl_bundle_properties() {
        TitanClient client = lifecycleClient();

        contextRunner
                .withBean(TitanClient.class, () -> client)
                .withPropertyValues(
                        "spring.titan.auto-start=false",
                        "spring.titan.ssl.bundle=titan-client",
                        "spring.titan.ssl.verify-hostname=false"
                )
                .run(context -> {
                    TitanProperties properties = context.getBean(TitanProperties.class);
                    assertThat(properties.getSsl().getBundle()).isEqualTo("titan-client");
                    assertThat(properties.getSsl().isVerifyHostname()).isFalse();
                });
    }

    @Test
    void creates_titan_client_from_pkcs12_ssl_bundle() throws Exception {
        Path trustStore = directory.resolve("titan-client.p12");
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, "changeit".toCharArray());
        try (OutputStream output = Files.newOutputStream(trustStore)) {
            keyStore.store(output, "changeit".toCharArray());
        }

        contextRunner
                .withPropertyValues(
                        "spring.titan.auto-start=false",
                        "spring.titan.endpoint=wss://localhost:8443/stomp",
                        "spring.titan.ssl.bundle=titan-client",
                        "spring.ssl.bundle.jks.titan-client.truststore.location=" + trustStore.toUri(),
                        "spring.ssl.bundle.jks.titan-client.truststore.password=changeit",
                        "spring.ssl.bundle.jks.titan-client.truststore.type=PKCS12"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(TitanClient.class).name()).isEqualTo("titan");
                });
    }

    @Test
    void creates_client_with_custom_connect_timeout() {
        TitanStompClientAutoConfiguration configuration = new TitanStompClientAutoConfiguration();
        TitanProperties properties = new TitanProperties();
        properties.setConnectTimeoutMillis(2000L);

        TitanClient client = configuration.titanStompClient(
                properties,
                noSslBundles()
        );

        assertThat(client.name()).isEqualTo("titan");
    }

    private static TitanClient lifecycleClient() {
        AtomicBoolean started = new AtomicBoolean(false);
        TitanClient client = mock(TitanClient.class);
        when(client.isStarted()).thenAnswer(invocation -> started.get());
        doAnswer(invocation -> {
            started.set(true);
            return null;
        }).when(client).start();
        when(client.connect()).thenReturn(CompletableFuture.completedFuture(client));
        return client;
    }

    private static ObjectProvider<SslBundles> noSslBundles() {
        return mock();
    }

    private static ObjectProvider<SslBundles> sslBundles(SslBundle sslBundle) {
        SslBundles bundles = mock(SslBundles.class);
        when(bundles.getBundle("titan-client")).thenReturn(sslBundle);
        ObjectProvider<SslBundles> provider = mock();
        when(provider.getIfAvailable()).thenReturn(bundles);
        return provider;
    }
}
