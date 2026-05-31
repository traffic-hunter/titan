package org.traffichunter.titan.springframework.stomp.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.traffichunter.titan.core.channel.EventLoopGroups;
import org.traffichunter.titan.core.resilience.retry.RetryPolicy;
import org.traffichunter.titan.core.transport.stomp.TitanStompClient;
import org.traffichunter.titan.core.transport.stomp.VertxStompClient;
import org.traffichunter.titan.core.transport.stomp.client.StompClient;
import org.traffichunter.titan.core.transport.stomp.client.StompClientOperations;
import org.traffichunter.titan.core.transport.stomp.client.StompClientProvider;
import org.traffichunter.titan.core.transport.stomp.option.StompClientOption;
import org.traffichunter.titan.springframework.stomp.TitanClientManager;
import org.traffichunter.titan.springframework.stomp.TitanProperties;

import java.time.Duration;
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
        properties.setClient("titan");

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
        properties.setClient("vertx");

        StompClient client = configuration.titanStompClient(
                List.of(provider),
                StompClientOption.builder().build(),
                properties
        );

        assertThat(client).isInstanceOf(VertxStompClient.class);
    }

    @Test
    void fails_when_client_has_no_provider() {
        TitanStompClientAutoConfiguration configuration = new TitanStompClientAutoConfiguration();
        TitanProperties properties = new TitanProperties();
        properties.setClient("missing");

        assertThatThrownBy(() -> configuration.titanStompClient(
                List.of(configuration.vertxStompClientProvider()),
                StompClientOption.builder().build(),
                properties
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing");
    }

    @Test
    void lifecycle_starts_and_connects_client_when_auto_start_and_auto_connect_are_enabled() {
        StompClientOperations operations = mock(StompClientOperations.class);
        StompClient client = lifecycleClient(operations);

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
        StompClientOperations operations = mock(StompClientOperations.class);
        StompClient client = lifecycleClient(operations);

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
        StompClientOperations operations = mock(StompClientOperations.class);
        StompClient client = lifecycleClient(operations);

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
    void binds_retry_properties() {
        StompClient client = lifecycleClient(mock(StompClientOperations.class));

        contextRunner
                .withBean(StompClient.class, () -> client)
                .withPropertyValues(
                        "spring.titan.auto-start=false",
                        "spring.titan.retry.enabled=true",
                        "spring.titan.retry.type=fix",
                        "spring.titan.retry.max-attempts=7",
                        "spring.titan.retry.delay=250ms",
                        "spring.titan.retry.max-delay=5s",
                        "spring.titan.retry.multiplier=3"
                )
                .run(context -> {
                    TitanProperties.Retry retry = context.getBean(TitanProperties.class).getRetry();
                    RetryPolicy policy = retry.toPolicy();

                    assertThat(retry.isEnabled()).isTrue();
                    assertThat(retry.getType()).isEqualTo(TitanProperties.Retry.Type.FIX);
                    assertThat(retry.getMaxAttempts()).isEqualTo(7);
                    assertThat(retry.getDelay()).isEqualTo(Duration.ofMillis(250));
                    assertThat(retry.getMaxDelay()).isEqualTo(Duration.ofSeconds(5));
                    assertThat(retry.getMultiplier()).isEqualTo(3);
                    assertThat(policy.maxAttempts()).isEqualTo(7);
                    assertThat(policy.delay(1)).isEqualTo(Duration.ofMillis(250));
                });
    }

    private static StompClient lifecycleClient(StompClientOperations operations) {
        AtomicBoolean started = new AtomicBoolean(false);
        StompClient client = mock(StompClient.class);
        when(client.isStarted()).thenAnswer(invocation -> started.get());
        doAnswer(invocation -> {
            started.set(true);
            return null;
        }).when(client).start();
        when(client.connect()).thenReturn(CompletableFuture.completedFuture(operations));
        when(client.operations()).thenReturn(operations);
        return client;
    }
}
