package org.traffichunter.titan.core.channel.stomp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.traffichunter.titan.core.codec.stomp.StompCommand;
import org.traffichunter.titan.core.codec.stomp.StompFrame;
import org.traffichunter.titan.core.codec.stomp.StompHeaders;
import org.traffichunter.titan.core.codec.stomp.StompHeaders.Elements;
import org.traffichunter.titan.core.codec.stomp.StompServerSubscription;
import org.traffichunter.titan.core.codec.stomp.StompServerSubscriptions;
import org.traffichunter.titan.core.transport.stomp.option.StompServerOption;
import org.traffichunter.titan.core.util.DestinationGroups;

/**
 * @author yun
 */
class StompServerHandlerImplSubscribeTest {

    private final StompServerSubscriptions subscriptions = new StompServerSubscriptions();
    private StompServerHandlerImpl handler;
    private StompClientChannel client;

    @BeforeEach
    void setUp() {
        StompServerChannel server = mock(StompServerChannel.class);
        when(server.option()).thenReturn(StompServerOption.builder().build());
        when(server.subscriptions()).thenReturn(subscriptions);
        handler = new StompServerHandlerImpl(server);

        client = mock(StompClientChannel.class);
        when(client.session()).thenReturn("session-1");
    }

    @Test
    void subscribe_with_group_header_registers_grouped_subscription() {
        handler.handle(subscribe("market"), client);

        StompServerSubscription subscription = subscriptions.find(client, "sub-1");
        assertThat(subscription).isNotNull();
        assertThat(subscription.getGroup()).isEqualTo("market");
        verify(client, never()).close();
    }

    @Test
    void subscribe_without_group_header_lands_in_default_group() {
        handler.handle(subscribe(null), client);

        StompServerSubscription subscription = subscriptions.find(client, "sub-1");
        assertThat(subscription).isNotNull();
        assertThat(subscription.getGroup()).isEqualTo(DestinationGroups.DEFAULT);
    }

    @Test
    void subscribe_with_invalid_group_is_rejected_with_error_frame() {
        handler.handle(subscribe("bad name"), client);

        ArgumentCaptor<StompFrame> sent = ArgumentCaptor.forClass(StompFrame.class);
        verify(client).send(sent.capture());
        assertThat(sent.getValue().getCommand()).isEqualTo(StompCommand.ERROR);
        assertThat(sent.getValue().getHeader(Elements.MESSAGE)).isEqualTo("Failed to subscribe.");
        verify(client).close();
        assertThat(subscriptions.size()).isZero();
    }

    private static StompFrame subscribe(String group) {
        StompFrame frame = StompFrame.create(StompHeaders.create(), StompCommand.SUBSCRIBE);
        frame.addHeader(Elements.DESTINATION, "/topic/price");
        frame.addHeader(Elements.ID, "sub-1");
        if (group != null) {
            frame.addHeader(Elements.GROUP, group);
        }
        return frame;
    }
}
