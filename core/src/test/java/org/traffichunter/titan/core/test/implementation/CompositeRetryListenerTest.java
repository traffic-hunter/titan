package org.traffichunter.titan.core.test.implementation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.traffichunter.titan.core.resilience.retry.CompositeRetryListener;
import org.traffichunter.titan.core.resilience.retry.RetryListener;

class CompositeRetryListenerTest {

    @Test
    void forwards_events_to_all_listeners_in_registration_order() {
        List<String> events = new ArrayList<>();
        CompositeRetryListener listeners = new CompositeRetryListener()
                .add(recordingListener("first", events))
                .add(recordingListener("second", events));
        IllegalStateException failure = new IllegalStateException("failed");

        listeners.onRetry(1, Duration.ofMillis(10));
        listeners.onRetryFailed(1, failure);
        listeners.onRetryExhausted(1, failure);

        assertThat(events).containsExactly(
                "first:retry",
                "second:retry",
                "first:failed",
                "second:failed",
                "first:exhausted",
                "second:exhausted"
        );
    }

    @Test
    void listener_can_be_added_after_chain_creation() {
        List<String> events = new ArrayList<>();
        CompositeRetryListener listeners = new CompositeRetryListener();

        listeners.add(recordingListener("added", events));
        listeners.onRetry(1, Duration.ZERO);

        assertThat(events).containsExactly("added:retry");
    }

    @Test
    void listener_added_during_dispatch_receives_the_next_event() {
        List<String> events = new ArrayList<>();
        CompositeRetryListener listeners = new CompositeRetryListener();
        RetryListener added = recordingListener("added", events);
        listeners.add(new RetryListener() {
            @Override
            public void onRetry(int attempt, Duration delay) {
                events.add("initial:retry:" + attempt);
                listeners.add(added);
            }
        });

        listeners.onRetry(1, Duration.ZERO);
        listeners.onRetry(2, Duration.ZERO);

        assertThat(events).containsExactly(
                "initial:retry:1",
                "initial:retry:2",
                "added:retry"
        );
    }

    @Test
    void duplicate_listener_instance_is_ignored() {
        RetryListener listener = new RetryListener() {
        };
        CompositeRetryListener listeners = new CompositeRetryListener()
                .add(listener)
                .add(listener);

        assertThat(listeners.listeners()).containsExactly(listener);
    }

    @Test
    void multiple_listeners_can_be_added_in_order() {
        RetryListener first = new RetryListener() {
        };
        RetryListener second = new RetryListener() {
        };
        CompositeRetryListener listeners = new CompositeRetryListener();

        CompositeRetryListener result = listeners.addAll(first, second);

        assertThat(result).isSameAs(listeners);
        assertThat(listeners.listeners()).containsExactly(first, second);
    }

    @Test
    void add_all_ignores_duplicate_listener_instances() {
        RetryListener listener = new RetryListener() {
        };
        CompositeRetryListener listeners = new CompositeRetryListener().add(listener);

        listeners.addAll(listener, listener);

        assertThat(listeners.listeners()).containsExactly(listener);
    }

    @Test
    void listener_can_be_added_at_a_specific_index() {
        RetryListener first = new RetryListener() {
        };
        RetryListener second = new RetryListener() {
        };
        RetryListener inserted = new RetryListener() {
        };
        CompositeRetryListener listeners = new CompositeRetryListener()
                .add(first)
                .add(second);

        CompositeRetryListener result = listeners.add(inserted, 1);

        assertThat(result).isSameAs(listeners);
        assertThat(listeners.listeners()).containsExactly(first, inserted, second);
    }

    @Test
    void indexed_add_ignores_duplicate_listener_instance() {
        RetryListener listener = new RetryListener() {
        };
        CompositeRetryListener listeners = new CompositeRetryListener().add(listener);

        listeners.add(listener, 0);

        assertThat(listeners.listeners()).containsExactly(listener);
    }

    @Test
    void listener_can_be_removed() {
        RetryListener listener = new RetryListener() {
        };
        CompositeRetryListener listeners = new CompositeRetryListener().add(listener);

        assertThat(listeners.remove(listener)).isTrue();
        assertThat(listeners.listeners()).isEmpty();
    }

    @Test
    void listeners_can_be_removed_by_assignable_type() {
        RetryListener first = new ChildRetryListener();
        RetryListener second = new ChildRetryListener();
        RetryListener remaining = new RetryListener() {
        };
        CompositeRetryListener listeners = new CompositeRetryListener()
                .add(first)
                .add(remaining)
                .add(second);

        CompositeRetryListener result = listeners.remove(ParentRetryListener.class);

        assertThat(result).isSameAs(listeners);
        assertThat(listeners.listeners()).containsExactly(remaining);
    }

    @Test
    void removing_an_unregistered_listener_returns_false() {
        CompositeRetryListener listeners = new CompositeRetryListener();

        assertThat(listeners.remove(new RetryListener() {
        })).isFalse();
    }

    @Test
    void clear_removes_all_listeners() {
        CompositeRetryListener listeners = new CompositeRetryListener()
                .add(new RetryListener() {
                })
                .add(new RetryListener() {
                });

        CompositeRetryListener result = listeners.clear();

        assertThat(result).isSameAs(listeners);
        assertThat(listeners.listeners()).isEmpty();
    }

    @Test
    void listeners_returns_an_immutable_snapshot() {
        RetryListener first = new RetryListener() {
        };
        RetryListener second = new RetryListener() {
        };
        CompositeRetryListener listeners = new CompositeRetryListener().add(first);
        List<RetryListener> snapshot = listeners.listeners();

        listeners.add(second);

        assertThat(snapshot).containsExactly(first);
        assertThat(listeners.listeners()).containsExactly(first, second);
        assertThatThrownBy(() -> snapshot.add(second))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static RetryListener recordingListener(String name, List<String> events) {
        return new RetryListener() {
            @Override
            public void onRetry(int attempt, Duration delay) {
                events.add(name + ":retry");
            }

            @Override
            public void onRetryFailed(int attempt, Throwable cause) {
                events.add(name + ":failed");
            }

            @Override
            public void onRetryExhausted(int attempt, Throwable cause) {
                events.add(name + ":exhausted");
            }
        };
    }

    private static class ParentRetryListener implements RetryListener {
    }

    private static final class ChildRetryListener extends ParentRetryListener {
    }
}
