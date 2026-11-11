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
    private final Destination news = Destination.create("/topic/news");

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
    void same_subscription_id_is_allowed_in_different_sessions() {
        StompServerSubscriptions subscriptions = new StompServerSubscriptions();
        StompServerSubscription first = subscription(null, "session-1", "sub-1");
        StompServerSubscription second = subscription(null, "session-2", "sub-1");

        assertThat(subscriptions.register(first)).isTrue();
        assertThat(subscriptions.register(second)).isTrue();
        assertThat(subscriptions.findByDestination(DestinationGroups.DEFAULT, destination))
                .containsExactlyInAnyOrder(first, second);
    }

    @Test
    void session_cannot_reuse_a_subscription_id_on_another_destination() {
        StompServerSubscriptions subscriptions = new StompServerSubscriptions();
        StompServerSubscription price = subscription(null, "session-1", "sub-1");
        StompServerSubscription duplicate = subscription(null, news, "session-1", "sub-1");

        assertThat(subscriptions.register(price)).isTrue();
        assertThat(subscriptions.register(duplicate)).isFalse();
        assertThat(subscriptions.findByDestination(DestinationGroups.DEFAULT, news)).isEmpty();
        assertThat(subscriptions.size()).isEqualTo(1);
    }

    @Test
    void unregister_removes_only_that_subscription() {
        StompServerSubscriptions subscriptions = new StompServerSubscriptions();
        StompServerSubscription first = subscription(null, "session-1", "sub-1");
        StompServerSubscription second = subscription(null, "session-2", "sub-1");
        subscriptions.register(first);
        subscriptions.register(second);

        assertThat(subscriptions.unregister(first.getConnection(), "sub-1")).isSameAs(first);
        assertThat(subscriptions.unregister(first.getConnection(), "sub-1")).isNull();
        assertThat(subscriptions.findByDestination(DestinationGroups.DEFAULT, destination)).containsExactly(second);
        assertThat(subscriptions.size()).isEqualTo(1);
    }

    @Test
    void unregister_all_removes_the_session_from_every_destination() {
        StompServerSubscriptions subscriptions = new StompServerSubscriptions();
        StompServerSubscription price = subscription(null, "session-1", "sub-1");
        StompServerSubscription newsOfSameSession = subscription(null, news, "session-1", "sub-2");
        StompServerSubscription other = subscription(null, "session-2", "sub-1");
        subscriptions.register(price);
        subscriptions.register(newsOfSameSession);
        subscriptions.register(other);

        assertThat(subscriptions.unregisterAll(price.getConnection())).isTrue();
        assertThat(subscriptions.unregisterAll(price.getConnection())).isFalse();
        assertThat(subscriptions.findByDestination(DestinationGroups.DEFAULT, destination)).containsExactly(other);
        assertThat(subscriptions.findByDestination(DestinationGroups.DEFAULT, news)).isEmpty();
        assertThat(subscriptions.size()).isEqualTo(1);
    }

    @Test
    void subscription_without_group_is_in_default_group() {
        StompServerSubscription subscription = subscription(null, "session-1", "sub-1");

        assertThat(subscription.getGroup()).isEqualTo(DestinationGroups.DEFAULT);
    }

    private StompServerSubscription subscription(String group, String session, String id) {
        return subscription(group, destination, session, id);
    }

    private StompServerSubscription subscription(String group, Destination destination, String session, String id) {
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
