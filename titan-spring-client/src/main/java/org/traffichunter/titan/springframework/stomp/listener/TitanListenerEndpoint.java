package org.traffichunter.titan.springframework.stomp.listener;

import java.lang.reflect.Method;

/**
 * Immutable description of a {@code @TitanListener} method.
 * Carries the target bean, method, destination, and client reference.
 * The registry uses this value to create listener containers.
 *
 * @author yun
 */
public record TitanListenerEndpoint(
    String id,
    String destination,
    Object bean,
    Method method,
    String clientRef,
    int concurrency
) {}
