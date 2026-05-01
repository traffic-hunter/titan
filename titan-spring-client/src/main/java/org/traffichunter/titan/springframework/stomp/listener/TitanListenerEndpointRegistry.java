package org.traffichunter.titan.springframework.stomp.listener;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.SmartLifecycle;
import org.traffichunter.titan.springframework.stomp.TitanClientManager;
import org.traffichunter.titan.springframework.stomp.factory.TitanListenerContainerFactory;

import java.util.ArrayList;
import java.util.List;

public final class TitanListenerEndpointRegistry implements SmartLifecycle {

    private final List<TitanListenerContainer> containers = new ArrayList<>();

    public void register(TitanListenerEndpoint endpoint, BeanFactory beanFactory) {
        TitanClientManager manager = beanFactory.getBean(endpoint.clientRef(), TitanClientManager.class);
        TitanListenerContainerFactory<?> factory = beanFactory.getBean(TitanListenerContainerFactory.class);
        containers.add(factory.create(endpoint, manager));
    }

    public void start() {
        containers.forEach(TitanListenerContainer::start);
    }

    public void stop() {
        containers.forEach(TitanListenerContainer::stop);
    }

    public boolean isRunning() {
        return containers.stream().allMatch(TitanListenerContainer::isRunning);
    }

    public boolean isStopped() { return containers.stream().allMatch(TitanListenerContainer::isStopped); }
}
