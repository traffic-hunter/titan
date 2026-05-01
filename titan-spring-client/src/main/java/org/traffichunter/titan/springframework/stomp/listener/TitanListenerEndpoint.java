package org.traffichunter.titan.springframework.stomp.listener;

import java.lang.reflect.Method;

public record TitanListenerEndpoint(
    String id,
    String destination,
    Object bean,
    Method method,
    String clientRef,
    int concurrency
) {}