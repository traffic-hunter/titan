package org.traffichunter.titan.springframework.stomp.listener;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.SmartLifecycle;
import org.traffichunter.titan.springframework.stomp.TitanClientManager;
import org.traffichunter.titan.springframework.stomp.factory.TitanListenerContainerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Registry for listener endpoints discovered from {@code @TitanListener}.
 * Creates listener containers through the configured factory.
 * Participates in the Spring lifecycle to start and stop all containers.
 *
 * @author yun
 */
public final class TitanListenerEndpointRegistry implements SmartLifecycle {

    private final List<TitanListenerContainer> containers = new ArrayList<>();

    /**
     * Register an endpoint and create its backing listener container.
     */
    public void register(TitanListenerEndpoint endpoint, BeanFactory beanFactory) {
        TitanClientManager manager = beanFactory.getBean(endpoint.clientRef(), TitanClientManager.class);
        TitanListenerContainerFactory<?> factory = beanFactory.getBean(TitanListenerContainerFactory.class);
        containers.add(factory.create(endpoint, manager));
    }

    /**
     * Start all registered listener containers.
     */
    public void start() {
        containers.forEach(TitanListenerContainer::start);
    }

    /**
     * Stop all registered listener containers.
     */
    public void stop() {
        containers.forEach(TitanListenerContainer::stop);
    }

    /**
     * Return whether all registered containers are running.
     */
    public boolean isRunning() {
        return containers.stream().allMatch(TitanListenerContainer::isRunning);
    }

    /**
     * Return whether all registered containers are stopped.
     */
    public boolean isStopped() {
        return containers.stream().allMatch(TitanListenerContainer::isStopped);
    }
}
