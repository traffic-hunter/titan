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
package org.traffichunter.titan.springframework.stomp.factory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.messaging.handler.invocation.HandlerMethodArgumentResolver;
import org.springframework.messaging.handler.invocation.HandlerMethodArgumentResolverComposite;
import org.springframework.util.ErrorHandler;
import org.traffichunter.titan.springframework.stomp.TitanClientManager;
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
