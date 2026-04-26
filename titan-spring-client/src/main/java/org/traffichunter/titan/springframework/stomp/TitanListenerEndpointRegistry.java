package org.traffichunter.titan.springframework.stomp;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.messaging.handler.invocation.HandlerMethodArgumentResolverComposite;
import org.springframework.retry.support.RetryTemplate;

import java.util.ArrayList;
import java.util.List;

public final class TitanListenerEndpointRegistry implements SmartLifecycle {

    private final List<TitanMessageListenerContainer> containers = new ArrayList<>();

    public void register(TitanListenerEndpoint endpoint, BeanFactory beanFactory) {
        TitanClientManager manager = beanFactory.getBean(endpoint.clientRef(), TitanClientManager.class);
        HandlerMethodArgumentResolverComposite resolvers = beanFactory.getBean(HandlerMethodArgumentResolverComposite.class);
        RetryTemplate retryTemplate = beanFactory.getBean(RetryTemplate.class);
        containers.add(new TitanMessageListenerContainer(endpoint, manager, resolvers, retryTemplate));
    }

    public void start() {
        containers.forEach(TitanMessageListenerContainer::start);
    }

    public void stop() {
        containers.forEach(TitanMessageListenerContainer::stop);
    }

    public boolean isRunning() {
        return containers.stream().allMatch(TitanMessageListenerContainer::isRunning);
    }
}
