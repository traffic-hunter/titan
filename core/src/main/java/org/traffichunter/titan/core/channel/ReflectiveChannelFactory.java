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
package org.traffichunter.titan.core.channel;

import java.lang.reflect.Constructor;

/**
 * Channel factory for built-in channel implementations with non-public constructors.
 *
 * <p>Core channel constructors are package-private to keep direct construction out of user
 * code. The factory resolves the constructor once and reuses it for each connection.</p>
 *
 * @author yun
 */
public final class ReflectiveChannelFactory<C extends Channel> implements ChannelFactory<C> {

    private final Constructor<? extends C> constructor;

    public ReflectiveChannelFactory(Class<? extends C> clazz) {
        try {
            this.constructor = clazz.getDeclaredConstructor(ChannelHandShakeEventListener.class);
            this.constructor.setAccessible(true);
        } catch (NoSuchMethodException e) {
            throw new ChannelException("Channel must have constructor(ChannelHandShakeEventListener): " + clazz.getName(), e);
        }
    }

    @Override
    public C create(ChannelHandShakeEventListener handShakeEventListener) {
        try {
            return constructor.newInstance(handShakeEventListener);
        } catch (Exception e) {
            throw new ChannelException("Failed to instantiate channel: " + e.getMessage(), e);
        }
    }

    @Override
    public void destroy(Channel channel) {
        channel.close();
    }
}
