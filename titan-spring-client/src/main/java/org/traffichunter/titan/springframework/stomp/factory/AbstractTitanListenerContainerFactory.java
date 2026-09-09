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
package org.traffichunter.titan.springframework.stomp.factory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.messaging.handler.invocation.HandlerMethodArgumentResolver;
import org.springframework.messaging.handler.invocation.HandlerMethodArgumentResolverComposite;
import org.springframework.util.ErrorHandler;
import org.traffichunter.titan.springframework.stomp.core.TitanClientManager;
import org.traffichunter.titan.springframework.stomp.listener.TitanListenerContainer;
import org.traffichunter.titan.springframework.stomp.listener.TitanListenerEndpoint;

/**
 * Base implementation for Titan listener container factories.
 * Collects common listener options and applies them during container creation.
 * Subclasses provide the concrete container instance.
 *
 * @author yun
 */
public abstract class AbstractTitanListenerContainerFactory<C extends TitanListenerContainer>
        implements TitanListenerContainerFactory<C> {

    private final List<HandlerMethodArgumentResolver> argumentResolvers = new ArrayList<>();
    private ErrorHandler listenerErrorHandler = throwable -> { };

    @Override
    public C create(TitanListenerEndpoint endpoint, TitanClientManager clientManager) {
        HandlerMethodArgumentResolverComposite composite = new HandlerMethodArgumentResolverComposite();
        argumentResolvers.forEach(composite::addResolver);
        return createContainerInstance(endpoint, clientManager, composite, listenerErrorHandler);
    }

    /**
     * Replace all argument resolvers used when invoking listener methods.
     */
    public void setArgumentResolvers(HandlerMethodArgumentResolver... argumentResolvers) {
        this.argumentResolvers.clear();
        this.argumentResolvers.addAll(Arrays.asList(argumentResolvers));
    }

    /**
     * Add an argument resolver to the listener invocation chain.
     */
    public void setArgumentResolver(HandlerMethodArgumentResolver argumentResolver) {
        this.argumentResolvers.add(argumentResolver);
    }

    /**
     * Set the handler invoked when listener method execution fails.
     */
    public void setListenerErrorHandler(ErrorHandler listenerErrorHandler) {
        this.listenerErrorHandler = listenerErrorHandler;
    }

    /**
     * Build the concrete container with resolved factory configuration.
     */
    protected abstract C createContainerInstance(
            TitanListenerEndpoint endpoint,
            TitanClientManager clientManager,
            HandlerMethodArgumentResolverComposite argumentResolvers,
            ErrorHandler listenerErrorHandler
    );
}
