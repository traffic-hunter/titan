package org.traffichunter.titan.dispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.DisplayNameGenerator.*;
import static org.traffichunter.titan.dispatch.DestinationGroupRegistry.DEFAULT_GROUP;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.Test;
import org.traffichunter.titan.core.util.Destination;
import org.traffichunter.titan.core.util.management.DispatcherQueueMbeans;

@DisplayNameGeneration(ReplaceUnderscores.class)
class DestinationGroupRegistryTest {

    private final List<String> created = new ArrayList<>();

    @AfterEach
    void unregisterQueueMbeans() {
        // Creating a queue registers a JMX MBean. The registry does not unregister it;
        // the gateway does. Clean up here so no MBeans are left behind.
        for (String path : created) {
            try {
                DispatcherQueueMbeans.unregister(path);
            } catch (RuntimeException ignored) {
                // already unregistered by the test body
            }
        }
    }

    @Test
    void ungrouped_queue_lands_in_default_group() {
        DestinationGroupRegistry registry = new DestinationGroupRegistry();
        Destination destination = destination("ungrouped");

        DispatcherQueue queue = registry.getOrPut(destination);

        DestinationGroup defaultGroup = registry.getGroup(DEFAULT_GROUP);
        assertThat(defaultGroup).isNotNull();
        assertThat(defaultGroup.get(destination)).isSameAs(queue);
        assertThat(registry.get(destination)).isSameAs(queue);
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

        DispatcherQueue marketQueue = market.getOrPut(marketOnly);
        registry.getOrPut(defaultOnly);

        assertThat(market.get(marketOnly)).isSameAs(marketQueue);
        assertThat(market.get(defaultOnly)).isNull();
        assertThat(market.exists(defaultOnly)).isFalse();
        assertThat(Objects.requireNonNull(registry.getGroup(DEFAULT_GROUP)).get(marketOnly)).isNull();
    }

    @Test
    void registry_sees_queues_from_every_group() {
        DestinationGroupRegistry registry = new DestinationGroupRegistry();
        DestinationGroup market = registry.getOrPutGroup("market");
        DestinationGroup notification = registry.getOrPutGroup("notification");
        Destination price = destination("union/price");
        Destination alerts = destination("union/alerts");
        Destination plain = destination("union/plain");

        DispatcherQueue priceQueue = market.getOrPut(price);
        DispatcherQueue alertsQueue = notification.getOrPut(alerts);
        DispatcherQueue plainQueue = registry.getOrPut(plain);

        assertThat(registry.get(price)).isSameAs(priceQueue);
        assertThat(registry.get(alerts)).isSameAs(alertsQueue);
        assertThat(registry.get(plain)).isSameAs(plainQueue);
        assertThat(registry.exists(price)).isTrue();
    }

    @Test
    void destination_cannot_belong_to_two_groups() {
        DestinationGroupRegistry registry = new DestinationGroupRegistry();
        DestinationGroup market = registry.getOrPutGroup("market");
        DestinationGroup notification = registry.getOrPutGroup("notification");
        Destination destination = destination("owned/price");
        DispatcherQueue owned = market.getOrPut(destination);

        assertThatThrownBy(() -> notification.getOrPut(destination))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("market")
                .hasMessageContaining("notification");

        assertThat(registry.get(destination)).isSameAs(owned);
        assertThat(notification.get(destination)).isNull();
    }

    @Test
    void registry_get_or_put_returns_queue_owned_by_another_group() {
        DestinationGroupRegistry registry = new DestinationGroupRegistry();
        DestinationGroup market = registry.getOrPutGroup("market");
        Destination destination = destination("shared/price");
        DispatcherQueue owned = market.getOrPut(destination);

        // Routing and fanout call the registry without a group name. They need the
        // existing queue. A second queue in the default group would break delivery.
        assertThat(registry.getOrPut(destination)).isSameAs(owned);
        assertThat(Objects.requireNonNull(registry.getGroup(DEFAULT_GROUP)).get(destination)).isNull();
    }

    @Test
    void group_wildcard_search_is_scoped_to_group() {
        DestinationGroupRegistry registry = new DestinationGroupRegistry();
        DestinationGroup market = registry.getOrPutGroup("market");
        DispatcherQueue first = market.getOrPut(destination("wild/1"));
        DispatcherQueue second = market.getOrPut(destination("wild/2"));
        registry.getOrPut(destination("wild/3"));

        List<DispatcherQueue> found = market.searchAll(Destination.create("/queue/group/wild/*"));

        assertThat(found).containsExactlyInAnyOrder(first, second);
    }

    @Test
    void registry_wildcard_search_spans_groups() {
        DestinationGroupRegistry registry = new DestinationGroupRegistry();
        DestinationGroup market = registry.getOrPutGroup("market");
        DispatcherQueue first = market.getOrPut(destination("span/1"));
        DispatcherQueue second = market.getOrPut(destination("span/2"));
        DispatcherQueue third = registry.getOrPut(destination("span/3"));

        List<DispatcherQueue> found = registry.searchAll(Destination.create("/queue/group/span/*"));

        assertThat(found).containsExactlyInAnyOrder(first, second, third);
        assertThat(registry.searchAll(Destination.create("/queue/group/span/1"))).containsExactly(first);
    }

    @Test
    void remove_clears_group_and_ownership() {
        DestinationGroupRegistry registry = new DestinationGroupRegistry();
        DestinationGroup market = registry.getOrPutGroup("market");
        DestinationGroup notification = registry.getOrPutGroup("notification");
        Destination destination = destination("remove/price");
        market.getOrPut(destination);

        registry.remove(destination);

        assertThat(registry.get(destination)).isNull();
        assertThat(market.get(destination)).isNull();

        // After removal another group can claim the destination. Unregister the old
        // MBean first, as the gateway would, so re-creating does not collide.
        DispatcherQueueMbeans.unregister(destination.path());
        DispatcherQueue recreated = notification.getOrPut(destination);

        assertThat(registry.get(destination)).isSameAs(recreated);
        assertThat(market.get(destination)).isNull();
    }

    @Test
    void group_remove_only_removes_queues_it_owns() {
        DestinationGroupRegistry registry = new DestinationGroupRegistry();
        DestinationGroup market = registry.getOrPutGroup("market");
        Destination destination = destination("foreign-remove/price");
        DispatcherQueue owned = market.getOrPut(destination);

        Objects.requireNonNull(registry.getGroup(DEFAULT_GROUP)).remove(destination);

        assertThat(registry.get(destination)).isSameAs(owned);

        market.remove(destination);

        assertThat(registry.get(destination)).isNull();
    }

    @Test
    void non_empty_group_cannot_be_removed() {
        DestinationGroupRegistry registry = new DestinationGroupRegistry();
        DestinationGroup market = registry.getOrPutGroup("market");
        Destination destination = destination("nonempty/price");
        market.getOrPut(destination);

        assertThat(registry.removeGroup("market")).isFalse();
        assertThat(registry.containsGroup("market")).isTrue();

        registry.remove(destination);

        assertThat(registry.removeGroup("market")).isTrue();
        assertThat(registry.getGroup("market")).isNull();
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
        DispatcherQueue grouped = registry.getOrPutGroup("market").getOrPut(destination("threshold/market"));
        DispatcherQueue ungrouped = dispatcher.getOrPut(destination("threshold/default"));

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

        DispatcherQueue queue = market.getOrPut(destination, 32);

        assertThat(queue.getMaxPendingBytes()).isEqualTo(32);
        assertThat(market.getOrPut(destination, 64)).isSameAs(queue);
        assertThat(queue.getMaxPendingBytes()).isEqualTo(32);
    }

    private Destination destination(String suffix) {
        Destination destination = Destination.create("/queue/group/" + suffix);
        created.add(destination.path());
        return destination;
    }
}
