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
package org.traffichunter.titan.springframework.stomp.listener;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.converter.*;
import org.springframework.messaging.handler.invocation.HandlerMethodArgumentResolverComposite;
import org.springframework.util.ClassUtils;
import org.traffichunter.titan.springframework.stomp.beans.TitanListenerAnnotationBeanPostProcessor;
import org.traffichunter.titan.springframework.stomp.factory.SimpleTitanListenerContainerFactory;
import org.traffichunter.titan.springframework.stomp.factory.TitanListenerContainerFactory;
import org.traffichunter.titan.springframework.stomp.messaging.converter.StompFrameMessageConverter;
import org.traffichunter.titan.springframework.stomp.messaging.resolver.TitanPayloadHandlerMethodArgumentResolver;
import org.traffichunter.titan.springframework.stomp.messaging.resolver.TitanStompHandlerMethodArgumentResolver;

import java.util.ArrayList;
import java.util.List;

/**
 * Spring configuration for annotation-driven Titan listeners.
 * Registers endpoint discovery, listener container factory, and argument resolvers.
 * Imported by {@code @EnableTitan}.
 *
 * @author yun
 */
@Configuration
public class TitanListenerConfiguration {

    @Bean
    TitanListenerEndpointRegistry titanListenerEndpointRegistry() {
        return new TitanListenerEndpointRegistry();
    }

    @Bean
    TitanListenerAnnotationBeanPostProcessor titanListenerAnnotationBeanPostProcessor(TitanListenerEndpointRegistry registry) {
        return new TitanListenerAnnotationBeanPostProcessor(registry);
    }

    @Bean
    public SmartMessageConverter titanMessageConverter() {
        List<MessageConverter> converters = new ArrayList<>();
        converters.add(new StompFrameMessageConverter());
        converters.add(new ByteArrayMessageConverter());
        converters.add(new StringMessageConverter());
        addJacksonConverter(converters);

        return new CompositeMessageConverter(converters);
    }

    @Bean
    public HandlerMethodArgumentResolverComposite titanResolverComposite(SmartMessageConverter converter) {
        HandlerMethodArgumentResolverComposite c = new HandlerMethodArgumentResolverComposite();
        c.addResolver(new TitanStompHandlerMethodArgumentResolver(converter));
        c.addResolver(new TitanPayloadHandlerMethodArgumentResolver(converter));
        return c;
    }

    @Bean
    public TitanListenerContainerFactory<?> titanListenerContainerFactory(SmartMessageConverter converter) {
        SimpleTitanListenerContainerFactory factory = new SimpleTitanListenerContainerFactory();
        factory.setArgumentResolvers(
                new TitanStompHandlerMethodArgumentResolver(converter),
                new TitanPayloadHandlerMethodArgumentResolver(converter)
        );
        return factory;
    }

    private void addJacksonConverter(List<MessageConverter> converters) {
        ClassLoader classLoader = getClass().getClassLoader();
        String converterClass;
        if (ClassUtils.isPresent("tools.jackson.databind.ObjectMapper", classLoader)
                && ClassUtils.isPresent(
                "org.springframework.messaging.converter.JacksonJsonMessageConverter",
                classLoader
        )) {
            converterClass = "org.springframework.messaging.converter.JacksonJsonMessageConverter";
        } else if (ClassUtils.isPresent("com.fasterxml.jackson.databind.ObjectMapper", classLoader)) {
            converterClass = "org.springframework.messaging.converter.MappingJackson2MessageConverter";
        } else {
            return;
        }

        try {
            Class<?> type = ClassUtils.forName(converterClass, classLoader);
            converters.add((MessageConverter) type.getDeclaredConstructor().newInstance());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to create Jackson message converter", e);
        }
    }
}
