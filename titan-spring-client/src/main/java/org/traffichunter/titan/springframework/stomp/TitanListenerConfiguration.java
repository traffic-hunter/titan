/*
The MIT License

Copyright (c) 2025 traffic-hunter

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
*/
package org.traffichunter.titan.springframework.stomp;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.converter.*;
import org.springframework.messaging.handler.invocation.HandlerMethodArgumentResolverComposite;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.traffichunter.titan.springframework.stomp.beans.TitanListenerAnnotationBeanPostProcessor;
import org.traffichunter.titan.springframework.stomp.messaging.converter.StompFrameMessageConverter;
import org.traffichunter.titan.springframework.stomp.messaging.resolver.TitanPayloadHandlerMethodArgumentResolver;
import org.traffichunter.titan.springframework.stomp.messaging.resolver.TitanStompHandlerMethodArgumentResolver;

import java.util.List;

/**
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
    SmartMessageConverter titanMessageConverter() {
        List<MessageConverter> converters = List.of(
                new StompFrameMessageConverter(),
                new ByteArrayMessageConverter(),
                new StringMessageConverter(),
                new MappingJackson2MessageConverter()
        );

        return new CompositeMessageConverter(converters);
    }

    @Bean
    HandlerMethodArgumentResolverComposite titanResolverComposite(SmartMessageConverter converter) {
        HandlerMethodArgumentResolverComposite c = new HandlerMethodArgumentResolverComposite();
        c.addResolver(new TitanStompHandlerMethodArgumentResolver(converter));
        c.addResolver(new TitanPayloadHandlerMethodArgumentResolver(converter));
        return c;
    }

    @Bean
    RetryTemplate titanListenerRetryTemplate(TitanProperties properties) {
        RetryTemplate retryTemplate = new RetryTemplate();
        retryTemplate.setRetryPolicy(new SimpleRetryPolicy(properties.getListenerMaxAttempts()));

        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(properties.getListenerInitialBackoffMillis());
        backOffPolicy.setMaxInterval(properties.getListenerMaxBackoffMillis());
        backOffPolicy.setMultiplier(properties.getListenerBackoffMultiplier());
        retryTemplate.setBackOffPolicy(backOffPolicy);

        return retryTemplate;
    }
}
