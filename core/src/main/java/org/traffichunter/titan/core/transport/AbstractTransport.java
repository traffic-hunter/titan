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
package org.traffichunter.titan.core.transport;

import java.net.SocketAddress;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.channel.Channel;
import org.traffichunter.titan.core.channel.ChannelHandShakeEventListener;
import org.traffichunter.titan.core.channel.EventLoopGroups;
import org.traffichunter.titan.core.channel.factory.ChannelFactory;
import org.traffichunter.titan.core.channel.factory.ReflectiveChannelFactory;
import org.traffichunter.titan.core.concurrent.Promise;
import org.traffichunter.titan.core.util.buffer.Buffer;
import org.traffichunter.titan.core.util.channel.ChannelRegistry;

/**
 * @author yun
 */
@Slf4j
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

    public boolean isStart() {
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

        if (channelRegistry.isClosed()) {
            eventLoopGroups.gracefullyShutdown(timeout, unit);
        }
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
