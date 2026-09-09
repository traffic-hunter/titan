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

import org.traffichunter.titan.springframework.stomp.core.TitanClientManager;
import org.traffichunter.titan.springframework.stomp.listener.TitanListenerContainer;
import org.traffichunter.titan.springframework.stomp.listener.TitanListenerEndpoint;

/**
 * Strategy for creating listener containers from discovered listener endpoints.
 * Implementations own container assembly and policy injection.
 * This mirrors Spring listener container factory patterns.
 *
 * @author yun
 */
public interface TitanListenerContainerFactory<C extends TitanListenerContainer> {

    /**
     * Create a listener container for the given endpoint and client manager.
     */
    C create(TitanListenerEndpoint endpoint, TitanClientManager clientManager);
}
