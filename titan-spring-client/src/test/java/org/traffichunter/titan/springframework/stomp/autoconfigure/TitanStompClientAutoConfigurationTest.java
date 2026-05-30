package org.traffichunter.titan.springframework.stomp.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.traffichunter.titan.core.channel.EventLoopGroups;
import org.traffichunter.titan.core.transport.stomp.TitanStompClient;
import org.traffichunter.titan.core.transport.stomp.VertxStompClient;
import org.traffichunter.titan.core.transport.stomp.client.StompClient;
import org.traffichunter.titan.core.transport.stomp.client.StompClientProvider;
import org.traffichunter.titan.core.transport.stomp.option.StompClientOption;
import org.traffichunter.titan.springframework.stomp.TitanProperties;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TitanStompClientAutoConfigurationTest {

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
}
