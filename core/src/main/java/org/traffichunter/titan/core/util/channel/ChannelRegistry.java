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
package org.traffichunter.titan.core.util.channel;

import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.channel.Channel;
import org.traffichunter.titan.core.channel.RoundRobinSelector;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * @author yun
 */
public final class ChannelRegistry<C extends Channel> {

    private final Map<String, C> channels = new ConcurrentHashMap<>();

    private final ChannelSelector<C> selector = new ChannelSelector<>(this);

    public ChannelSelector<C> selector() { return selector; }

    public void addChannel(C channel) {
        addChannel(channel.id(), channel);
    }

    public void addChannel(String key, C channel) {
        channels.put(key, channel);
    }

    public @Nullable C getChannel(String key) {
        return channels.get(key);
    }

    public void removeChannel(C channel) {
        channels.remove(channel.id());
    }

    public void removeChannel(String key) {
        channels.remove(key);
    }

    Map<String, C> getChannelMap() {
        return channels;
    }

    public static class ChannelSelector<C extends Channel> implements Iterator<C> {

        private final ChannelRegistry<C> registry;

        public ChannelSelector(ChannelRegistry<C> registry) {
            this.registry = registry;
        }

        @Override
        public boolean hasNext() {
            return stream().iterator().hasNext();
        }

        @Override
        public C next() {
            return stream().iterator().next();
        }

        public Stream<C> stream() {
            return registry.getChannelMap().values().stream();
        }

        public void forEachChannel(Consumer<C> consumer) {
            registry.getChannelMap().values().forEach(consumer);
        }

        public int size() {
            return registry.getChannelMap().size();
        }
    }
}
