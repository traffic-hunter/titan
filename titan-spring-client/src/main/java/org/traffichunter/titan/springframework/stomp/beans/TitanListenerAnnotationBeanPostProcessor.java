/*
 * Copyright 2025 traffic-hunter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traffichunter.titan.springframework.stomp.beans;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.Assert;
import org.traffichunter.titan.core.util.DestinationGroups;
import org.traffichunter.titan.springframework.stomp.annotation.TitanListener;
import org.traffichunter.titan.springframework.stomp.listener.TitanListenerEndpoint;
import org.traffichunter.titan.springframework.stomp.listener.TitanListenerEndpointRegistry;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Detects {@link TitanListener} methods after bean initialization.
 * Builds listener endpoint metadata and registers it after singleton creation.
 * This keeps endpoint discovery aligned with Spring's bean lifecycle.
 *
 * @author yun
 */
public class TitanListenerAnnotationBeanPostProcessor implements BeanPostProcessor, BeanFactoryAware, SmartInitializingSingleton {

    private final List<TitanListenerEndpoint> endpoints = new ArrayList<>();
    private final TitanListenerEndpointRegistry registry;

    private @Nullable BeanFactory beanFactory;

    public TitanListenerAnnotationBeanPostProcessor(TitanListenerEndpointRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
    }

    @Override
    public @Nullable Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        for(Method method : bean.getClass().getMethods()) {
            TitanListener titanListener = AnnotatedElementUtils.findMergedAnnotation(method, TitanListener.class);
            if(titanListener == null) {
                continue;
            }

            String id = titanListener.id().isBlank() ? beanName + "#" + method.getName() : titanListener.id();
            TitanListenerEndpoint endpoint = new TitanListenerEndpoint(
                    id,
                    resolveGroup(titanListener.group(), method),
                    titanListener.destination(),
                    bean,
                    method,
                    titanListener.clientRef(),
                    titanListener.concurrency()
            );

            endpoints.add(endpoint);
        }

        return bean;
    }

    /** Endpoints discovered so far, in discovery order. */
    List<TitanListenerEndpoint> endpoints() {
        return List.copyOf(endpoints);
    }

    /**
     * Resolves a configured group name and refuses one the broker would reject.
     *
     * <p>A property reference is substituted first. A placeholder that nothing resolves, or a
     * name outside the group naming rule, fails here rather than at the first SUBSCRIBE.</p>
     */
    private String resolveGroup(String group, Method method) {
        String resolved = group;
        if (beanFactory instanceof ConfigurableBeanFactory configurableBeanFactory) {
            try {
                resolved = configurableBeanFactory.resolveEmbeddedValue(group);
            } catch (RuntimeException error) {
                throw new IllegalStateException(
                        "Failed to resolve @TitanListener group for " + method, error);
            }
        }

        try {
            return DestinationGroups.normalize(resolved);
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException(
                    "Invalid @TitanListener group for " + method + ": " + resolved, error);
        }
    }

    @Override
    public void afterSingletonsInstantiated() {
        Assert.notNull(beanFactory, "beanFactory must not be null");

        endpoints.forEach(endpoint -> registry.register(endpoint, beanFactory));
    }
}
