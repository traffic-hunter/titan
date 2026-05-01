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
package org.traffichunter.titan.springframework.stomp.beans;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.Assert;
import org.traffichunter.titan.springframework.stomp.annotation.TitanListener;
import org.traffichunter.titan.springframework.stomp.listener.TitanListenerEndpoint;
import org.traffichunter.titan.springframework.stomp.listener.TitanListenerEndpointRegistry;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
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

    @Override
    public void afterSingletonsInstantiated() {
        Assert.notNull(beanFactory, "beanFactory must not be null");

        endpoints.forEach(endpoint -> registry.register(endpoint, beanFactory));
    }
}
