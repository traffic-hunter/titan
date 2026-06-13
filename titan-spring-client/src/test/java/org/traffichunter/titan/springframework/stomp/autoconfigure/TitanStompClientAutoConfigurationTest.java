package org.traffichunter.titan.springframework.stomp.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.traffichunter.titan.core.channel.EventLoopGroups;
import org.traffichunter.titan.core.transport.stomp.TitanStompClient;
import org.traffichunter.titan.core.transport.stomp.VertxStompClient;
import org.traffichunter.titan.core.transport.stomp.client.StompClient;
import org.traffichunter.titan.core.transport.stomp.client.StompConnection;
import org.traffichunter.titan.core.transport.stomp.client.StompClientProvider;
import org.traffichunter.titan.core.transport.stomp.option.StompClientOption;
import org.traffichunter.titan.springframework.stomp.core.TitanClientManager;
import org.traffichunter.titan.springframework.stomp.TitanProperties;
import org.traffichunter.titan.springframework.stomp.core.TitanTemplate;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TitanStompClientAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TitanStompClientAutoConfiguration.class));

    @Test
    void creates_titan_client_from_titan_client() {
        TitanStompClientAutoConfiguration configuration = new TitanStompClientAutoConfiguration();
        ObjectProvider<EventLoopGroups> eventLoopGroups = mock();
        when(eventLoopGroups.getObject()).thenReturn(mock(EventLoopGroups.class));
        StompClientProvider provider = configuration.titanStompClientProvider(eventLoopGroups);
        TitanProperties properties = new TitanProperties();
        properties.setClient(TitanProperties.Client.TITAN);

        StompClient client = configuration.titanStompClient(
                List.of(provider),
                StompClientOption.builder().build(),
                properties
        );

        assertThat(client).isInstanceOf(TitanStompClient.class);
    }

    @Test
    void creates_vertx_client_from_vertx_client() {
        TitanStompClientAutoConfiguration configuration = new TitanStompClientAutoConfiguration();
        StompClientProvider provider = configuration.vertxStompClientProvider();
        TitanProperties properties = new TitanProperties();
        properties.setClient(TitanProperties.Client.VERTX);

        StompClient client = configuration.titanStompClient(
                List.of(provider),
                StompClientOption.builder().build(),
                properties
        );

        assertThat(client).isInstanceOf(VertxStompClient.class);
    }

    @Test
    void lifecycle_starts_and_connects_client_when_auto_start_and_auto_connect_are_enabled() {
        StompConnection connection = mock(StompConnection.class);
        StompClient client = lifecycleClient(connection);

        contextRunner
                .withBean(StompClient.class, () -> client)
                .run(context -> {
                    assertThat(context).hasSingleBean(TitanClientManager.class);
                    assertThat(context.getBean(TitanClientManager.class).isRunning()).isTrue();
                    verify(client).start();
                    verify(client).connect();
                });
    }

    @Test
    void lifecycle_only_starts_client_when_auto_connect_is_disabled() {
        StompConnection connection = mock(StompConnection.class);
        StompClient client = lifecycleClient(connection);

        contextRunner
                .withBean(StompClient.class, () -> client)
                .withPropertyValues("spring.titan.auto-connect=false")
                .run(context -> {
                    assertThat(context.getBean(TitanClientManager.class).isRunning()).isTrue();
                    verify(client).start();
                    verify(client, never()).connect();
                });
    }

    @Test
    void lifecycle_does_not_start_client_when_auto_start_is_disabled() {
        StompConnection connection = mock(StompConnection.class);
        StompClient client = lifecycleClient(connection);

        contextRunner
                .withBean(StompClient.class, () -> client)
                .withPropertyValues("spring.titan.auto-start=false")
                .run(context -> {
                    assertThat(context.getBean(TitanClientManager.class).isRunning()).isFalse();
                    verify(client, never()).start();
                    verify(client, never()).connect();
                });
    }

    @Test
    void exposes_client_manager_and_template_with_public_bean_names() {
        StompClient client = lifecycleClient(mock(StompConnection.class));

        contextRunner
                .withBean(StompClient.class, () -> client)
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
        StompClient client = lifecycleClient(mock(StompConnection.class));

        contextRunner
                .withBean(StompClient.class, () -> client)
                .withPropertyValues(
                        "spring.titan.auto-start=false",
                        "spring.titan.client=vertx"
                )
                .run(context -> {
                    TitanProperties properties = context.getBean(TitanProperties.class);

                    assertThat(properties.getClient()).isEqualTo(TitanProperties.Client.VERTX);
                });
    }

    private static StompClient lifecycleClient(StompConnection connection) {
        AtomicBoolean started = new AtomicBoolean(false);
        StompClient client = mock(StompClient.class);
        when(client.isStarted()).thenAnswer(invocation -> started.get());
        doAnswer(invocation -> {
            started.set(true);
            return null;
        }).when(client).start();
        when(client.connect()).thenReturn(CompletableFuture.completedFuture(connection));
        when(client.connection()).thenReturn(connection);
        return client;
    }
}
