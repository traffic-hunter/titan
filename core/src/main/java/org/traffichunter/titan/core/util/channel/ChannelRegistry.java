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
import org.traffichunter.titan.core.util.selector.RoundRobinSelector;
import org.traffichunter.titan.core.util.selector.Selector;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * @author yun
 */
public final class ChannelRegistry<C extends Channel> {

    private final Map<String, C> channels = new ConcurrentHashMap<>();

    private final ChannelSelector<C> selector = new ChannelSelector<>(this, new RoundRobinSelector<>());

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
        removeChannel(channel.id());
    }

    public void removeChannel(String key) {
        channels.remove(key);
    }

    public List<C> getChannels() {
        return channels.values().stream().toList();
    }

    public void forEach(Consumer<C> consumer) {
        channels.values().forEach(consumer);
    }

    public boolean isActive() {
        List<C> snapshot = getChannels();
        return !snapshot.isEmpty() && snapshot.stream().allMatch(Channel::isActive);
    }

    public boolean isClosed() {
        return channels.values().stream().allMatch(Channel::isClosed);
    }

    public boolean isEmpty() {
        return channels.isEmpty();
    }

    public static class ChannelSelector<C extends Channel> {

        private final ChannelRegistry<C> registry;
        private final Selector<C> selector;

        public ChannelSelector(ChannelRegistry<C> registry, Selector<C> selector) {
            this.registry = registry;
            this.selector = selector;
        }

        public List<C> channels() {
            return registry.getChannels();
        }

        public C next() {
            return selector.next(registry.channels.values());
        }
    }
}
