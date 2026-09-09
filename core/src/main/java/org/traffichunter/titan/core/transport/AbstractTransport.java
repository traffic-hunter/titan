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
package org.traffichunter.titan.core.transport;

import java.net.SocketAddress;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.channel.Channel;
import org.traffichunter.titan.core.channel.ChannelHandShakeEventListener;
import org.traffichunter.titan.core.channel.EventLoopGroups;
import org.traffichunter.titan.core.channel.ChannelFactory;
import org.traffichunter.titan.core.channel.ReflectiveChannelFactory;
import org.traffichunter.titan.core.util.concurrent.Promise;
import org.traffichunter.titan.core.util.buffer.Buffer;
import org.traffichunter.titan.core.channel.ChannelRegistry;

/**
 * Base transport that owns event loops and the channels created for the transport.
 *
 * <p>Subclasses decide the transport lifecycle, while this class centralizes channel creation,
 * registry management, and common shutdown behavior.</p>
 *
 * @author yun
 */
public abstract class AbstractTransport<C extends Channel> {

    private final EventLoopGroups eventLoopGroups;
    private final ChannelFactory<C> channelFactory;
    protected final ChannelRegistry<C> channelRegistry;

    protected AbstractTransport(Class<? extends C> clazz, EventLoopGroups eventLoopGroups) {
        this.eventLoopGroups = eventLoopGroups;
        this.channelFactory = new ReflectiveChannelFactory<>(clazz);
        this.channelRegistry = new ChannelRegistry<>();
    }

    public abstract void start();

    public boolean isStarted() {
        return channelRegistry.isActive();
    }

    public boolean isShutdown() {
        return channelRegistry.isClosed() && eventLoopGroups.isShutdown();
    }

    public @Nullable SocketAddress remoteAddress() {
        C channel = channel();
        return channel.remoteAddress();
    }

    public @Nullable SocketAddress localAddress() {
        C channel = channel();
        return channel.localAddress();
    }

    public abstract Promise<Void> send(Buffer buffer);

    public abstract void shutdown(long timeout, TimeUnit unit);

    public String version() {
        return "1.0";
    }

    public List<C> channels() {
        return channelRegistry.getChannels();
    }

    protected C newChannel(ChannelHandShakeEventListener handShakeEventListener) {
        C channel = channelFactory.create(handShakeEventListener);
        channelRegistry.addChannel(channel);
        return channel;
    }

    protected void destroyChannel(C channel) {
        channelFactory.destroy(channel);
        channelRegistry.removeChannel(channel);
    }

    public void close(long timeout, TimeUnit unit) {
        channelRegistry.forEach(Channel::close);
        eventLoopGroups.gracefullyShutdown(timeout, unit);
    }

    public boolean isClosed() {
        return channelRegistry.isClosed();
    }

    public C channel() {
        return channelRegistry.selector().next();
    }

    protected EventLoopGroups groups() {
        return eventLoopGroups;
    }
}
