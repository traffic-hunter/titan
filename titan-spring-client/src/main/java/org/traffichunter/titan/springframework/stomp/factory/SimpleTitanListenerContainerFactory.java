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

import org.springframework.util.ErrorHandler;
import org.traffichunter.titan.springframework.stomp.core.TitanClientManager;
import org.traffichunter.titan.springframework.stomp.listener.TitanListenerContainer;
import org.traffichunter.titan.springframework.stomp.listener.TitanListenerEndpoint;
import org.springframework.messaging.handler.invocation.HandlerMethodArgumentResolverComposite;

/**
 * Default listener container factory for annotation-driven Titan listeners.
 * Creates the standard {@link TitanListenerContainer}.
 * Custom listener policies can be configured through inherited setters.
 *
 * @author yun
 */
public final class SimpleTitanListenerContainerFactory
        extends AbstractTitanListenerContainerFactory<TitanListenerContainer> {

    /**
     * Create the default listener container instance.
     */
    @Override
    protected TitanListenerContainer createContainerInstance(
            TitanListenerEndpoint endpoint,
            TitanClientManager clientManager,
            HandlerMethodArgumentResolverComposite argumentResolvers,
            ErrorHandler listenerErrorHandler
    ) {
        return new TitanListenerContainer(endpoint, clientManager, argumentResolvers, listenerErrorHandler);
    }
}
