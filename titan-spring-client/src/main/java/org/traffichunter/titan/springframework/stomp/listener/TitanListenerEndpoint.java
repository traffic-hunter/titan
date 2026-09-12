package org.traffichunter.titan.springframework.stomp.listener;

import java.lang.reflect.Method;

import org.traffichunter.titan.core.util.DestinationGroups;

/**
 * Immutable description of a {@code @TitanListener} method.
 * Carries the target bean, method, destination group, destination, and client reference.
 * The registry uses this value to create listener containers.
 *
 * @author yun
 */
public record TitanListenerEndpoint(
    String id,
    String group,
    String destination,
    Object bean,
    Method method,
    String clientRef,
    int concurrency
) {

    /** A blank group means the default one, and a malformed name is refused here. */
    public TitanListenerEndpoint {
        group = DestinationGroups.normalize(group);
    }

    /** Describes a listener on the default group. */
    public TitanListenerEndpoint(
            String id,
            String destination,
            Object bean,
            Method method,
            String clientRef,
            int concurrency
    ) {
        this(id, DestinationGroups.DEFAULT, destination, bean, method, clientRef, concurrency);
    }
}
