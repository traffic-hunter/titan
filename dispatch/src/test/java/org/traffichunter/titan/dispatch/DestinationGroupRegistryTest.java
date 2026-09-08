package org.traffichunter.titan.dispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.traffichunter.titan.dispatch.DestinationGroupRegistry.DEFAULT_GROUP;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import javax.management.MBeanServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.traffichunter.titan.core.util.Destination;
import org.traffichunter.titan.core.util.management.DispatcherQueueMbeans;

class DestinationGroupRegistryTest {

    private final List<DispatcherQueue> created = new ArrayList<>();

    @AfterEach
    void unregisterQueueMbeans() {
        // Creating a queue registers a JMX MBean. The registry does not unregister it;
        // the gateway does. Clean up here so no MBeans are left behind.
        for (DispatcherQueue queue : created) {
            try {
                DispatcherQueueMbeans.unregister(queue.getGroup(), queue.getDestination());
            } catch (RuntimeException ignored) {
                // already unregistered by the test body
            }
        }
    }

    @Test
    void ungrouped_queue_lands_in_default_group() {
        DestinationGroupRegistry registry = new DestinationGroupRegistry();
        Destination destination = destination("ungrouped");

        DispatcherQueue queue = track(registry.getOrPut(destination));

        DestinationGroup defaultGroup = registry.getGroup(DEFAULT_GROUP);
        assertThat(defaultGroup).isNotNull();
        assertThat(defaultGroup.get(destination)).isSameAs(queue);
        assertThat(registry.get(destination)).isSameAs(queue);
        assertThat(queue.getGroup()).isEqualTo(DEFAULT_GROUP);
    }

    @Test
    void get_or_put_group_is_idempotent() {
        DestinationGroupRegistry registry = new DestinationGroupRegistry();

        DestinationGroup first = registry.getOrPutGroup("market");
        DestinationGroup second = registry.getOrPutGroup("market");

        assertThat(second).isSameAs(first);
        assertThat(first.name()).isEqualTo("market");
        assertThat(first.id()).isNotBlank();
        assertThat(registry.containsGroup("market")).isTrue();
        assertThat(registry.groups())
                .extracting(DestinationGroup::name)
                .containsExactlyInAnyOrder(DEFAULT_GROUP, "market");
    }

    @Test
    void group_sees_only_its_own_queues() {
        DestinationGroupRegistry registry = new DestinationGroupRegistry();
        DestinationGroup market = registry.getOrPutGroup("market");
        Destination marketOnly = destination("scope/market");
        Destination defaultOnly = destination("scope/default");

        DispatcherQueue marketQueue = track(market.getOrPut(marketOnly));
        track(registry.getOrPut(defaultOnly));

        assertThat(market.get(marketOnly)).isSameAs(marketQueue);
        assertThat(marketQueue.getGroup()).isEqualTo("market");
        assertThat(market.get(defaultOnly)).isNull();
        assertThat(market.exists(defaultOnly)).isFalse();
        assertThat(registry.getGroup(DEFAULT_GROUP).get(marketOnly)).isNull();
    }

    @Test
    void same_destination_can_exist_in_different_groups() {
        DestinationGroupRegistry registry = new DestinationGroupRegistry();
        DestinationGroup market = registry.getOrPutGroup("market");
        DestinationGroup notification = registry.getOrPutGroup("notification");
        Destination destination = destination("shared/price");

        DispatcherQueue marketQueue = track(market.getOrPut(destination));
        DispatcherQueue notificationQueue = track(notification.getOrPut(destination));

        assertThat(notificationQueue).isNotSameAs(marketQueue);
        assertThat(market.get(destination)).isSameAs(marketQueue);
        assertThat(notification.get(destination)).isSameAs(notificationQueue);
        assertThat(marketQueue.getGroup()).isEqualTo("market");
        assertThat(notificationQueue.getGroup()).isEqualTo("notification");
    }

    @Test
    void registry_dispatcher_calls_use_the_default_namespace() {
        DestinationGroupRegistry registry = new DestinationGroupRegistry();
        DestinationGroup market = registry.getOrPutGroup("market");
        Destination destination = destination("namespace/price");
        DispatcherQueue marketQueue = track(market.getOrPut(destination));

        // Requests without a group never see the market queue. Routing and fanout use
        // these calls, so a group queue only receives traffic once a group is named.
        assertThat(registry.get(destination)).isNull();
        assertThat(registry.exists(destination)).isFalse();
        assertThat(registry.searchAll(destination)).isEmpty();

        DispatcherQueue defaultQueue = track(registry.getOrPut(destination));

        assertThat(defaultQueue).isNotSameAs(marketQueue);
        assertThat(defaultQueue.getGroup()).isEqualTo(DEFAULT_GROUP);
        assertThat(registry.get(destination)).isSameAs(defaultQueue);
        assertThat(market.get(destination)).isSameAs(marketQueue);
    }

    @Test
    void group_wildcard_search_is_scoped_to_group() {
        DestinationGroupRegistry registry = new DestinationGroupRegistry();
        DestinationGroup market = registry.getOrPutGroup("market");
        DispatcherQueue first = track(market.getOrPut(destination("wild/1")));
        DispatcherQueue second = track(market.getOrPut(destination("wild/2")));
        DispatcherQueue third = track(registry.getOrPut(destination("wild/3")));

        Destination pattern = Destination.create("/queue/group/wild/*");

        assertThat(market.searchAll(pattern)).containsExactlyInAnyOrder(first, second);
        assertThat(registry.searchAll(pattern)).containsExactly(third);
    }

    @Test
    void remove_only_affects_own_group() {
        DestinationGroupRegistry registry = new DestinationGroupRegistry();
        DestinationGroup market = registry.getOrPutGroup("market");
        DestinationGroup notification = registry.getOrPutGroup("notification");
        Destination destination = destination("remove/price");
        track(market.getOrPut(destination));
        DispatcherQueue notificationQueue = track(notification.getOrPut(destination));

        market.remove(destination);

        assertThat(market.get(destination)).isNull();
        assertThat(notification.get(destination)).isSameAs(notificationQueue);

        // The default group never had this destination. Removing it there is a no-op.
        registry.remove(destination);

        assertThat(notification.get(destination)).isSameAs(notificationQueue);
    }

    @Test
    void non_empty_group_cannot_be_removed() {
        DestinationGroupRegistry registry = new DestinationGroupRegistry();
        DestinationGroup market = registry.getOrPutGroup("market");
        Destination destination = destination("nonempty/price");
        track(market.getOrPut(destination));

        assertThat(registry.removeGroup("market")).isFalse();
        assertThat(registry.containsGroup("market")).isTrue();

        market.remove(destination);

        assertThat(registry.removeGroup("market")).isTrue();
        assertThat(registry.getGroup("market")).isNull();
    }

    @Test
    void removed_group_rejects_new_queues_and_is_replaced_on_next_lookup() {
        DestinationGroupRegistry registry = new DestinationGroupRegistry();
        DestinationGroup stale = registry.getOrPutGroup("stale");
        Destination destination = destination("stale/price");

        assertThat(registry.removeGroup("stale")).isTrue();

        assertThatThrownBy(() -> stale.getOrPut(destination))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("removed");

        DestinationGroup replacement = registry.getOrPutGroup("stale");
        assertThat(replacement).isNotSameAs(stale);
        track(replacement.getOrPut(destination));
    }

    @Test
    void default_group_cannot_be_removed() {
        DestinationGroupRegistry registry = new DestinationGroupRegistry();

        assertThat(registry.removeGroup(DEFAULT_GROUP)).isFalse();
        assertThat(registry.getGroup(DEFAULT_GROUP)).isNotNull();
    }

    @Test
    void removing_unknown_group_returns_false() {
        DestinationGroupRegistry registry = new DestinationGroupRegistry();

        assertThat(registry.removeGroup("missing")).isFalse();
    }

    @Test
    void blank_group_name_is_rejected() {
        DestinationGroupRegistry registry = new DestinationGroupRegistry();

        assertThatThrownBy(() -> registry.getOrPutGroup("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void default_dispatcher_is_group_registry_with_inherited_thresholds() {
        Dispatcher dispatcher = Dispatcher.getDefault(128);

        assertThat(dispatcher).isInstanceOf(DestinationGroupRegistry.class);

        DestinationGroupRegistry registry = (DestinationGroupRegistry) dispatcher;
        DispatcherQueue grouped = track(registry.getOrPutGroup("market").getOrPut(destination("threshold/market")));
        DispatcherQueue ungrouped = track(dispatcher.getOrPut(destination("threshold/default")));

        long expectedResume = DestinationQueueMetadata.defaultResumePendingBytes(128);
        assertThat(grouped.getMaxPendingBytes()).isEqualTo(128);
        assertThat(grouped.getResumePendingBytes()).isEqualTo(expectedResume);
        assertThat(ungrouped.getMaxPendingBytes()).isEqualTo(128);
        assertThat(ungrouped.getResumePendingBytes()).isEqualTo(expectedResume);
    }

    @Test
    void explicit_byte_limit_applies_to_new_queue_only() {
        DestinationGroupRegistry registry = new DestinationGroupRegistry();
        DestinationGroup market = registry.getOrPutGroup("market");
        Destination destination = destination("limit/price");

        DispatcherQueue queue = track(market.getOrPut(destination, 32));

        assertThat(queue.getMaxPendingBytes()).isEqualTo(32);
        assertThat(market.getOrPut(destination, 64)).isSameAs(queue);
        assertThat(queue.getMaxPendingBytes()).isEqualTo(32);
    }

    @Test
    void same_destination_in_two_groups_registers_two_mbeans() {
        DestinationGroupRegistry registry = new DestinationGroupRegistry();
        Destination destination = destination("mbean/price");
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();

        track(registry.getOrPutGroup("mbean-a").getOrPut(destination));
        track(registry.getOrPutGroup("mbean-b").getOrPut(destination));

        // With the group in the name the second queue no longer replaces the first.
        assertThat(server.isRegistered(DispatcherQueueMbeans.objectName("mbean-a", destination.path()))).isTrue();
        assertThat(server.isRegistered(DispatcherQueueMbeans.objectName("mbean-b", destination.path()))).isTrue();
        assertThat(server.isRegistered(DispatcherQueueMbeans.objectName(destination.path()))).isFalse();
    }

    private Destination destination(String suffix) {
        return Destination.create("/queue/group/" + suffix);
    }

    private DispatcherQueue track(DispatcherQueue queue) {
        created.add(queue);
        return queue;
    }
}
