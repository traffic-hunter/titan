package org.traffichunter.titan.core.codec.stomp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.traffichunter.titan.core.channel.stomp.StompClientChannel;
import org.traffichunter.titan.core.util.Destination;
import org.traffichunter.titan.core.util.DestinationGroups;

/**
 * @author yun
 */
class StompServerSubscriptionsTest {

    private final Destination destination = Destination.create("/topic/price");

    @Test
    void find_by_destination_filters_by_group() {
        StompServerSubscriptions subscriptions = new StompServerSubscriptions();
        StompServerSubscription market = subscription("market", "session-1", "sub-1");
        StompServerSubscription notification = subscription("notification", "session-2", "sub-2");
        StompServerSubscription plain = subscription(null, "session-3", "sub-3");
        subscriptions.register(market);
        subscriptions.register(notification);
        subscriptions.register(plain);

        assertThat(subscriptions.findByDestination("market", destination)).containsExactly(market);
        assertThat(subscriptions.findByDestination("notification", destination)).containsExactly(notification);
        assertThat(subscriptions.findByDestination(DestinationGroups.DEFAULT, destination)).containsExactly(plain);
        assertThat(subscriptions.findByDestination("unknown", destination)).isEmpty();
    }

    @Test
    void subscription_without_group_is_in_default_group() {
        StompServerSubscription subscription = subscription(null, "session-1", "sub-1");

        assertThat(subscription.getGroup()).isEqualTo(DestinationGroups.DEFAULT);
    }

    private StompServerSubscription subscription(String group, String session, String id) {
        StompClientChannel connection = mock(StompClientChannel.class);
        when(connection.session()).thenReturn(session);
        return StompServerSubscription.builder()
                .group(group)
                .destination(destination)
                .id(id)
                .ackMode(StompFrame.AckMode.AUTO)
                .connection(connection)
                .build();
    }
}
