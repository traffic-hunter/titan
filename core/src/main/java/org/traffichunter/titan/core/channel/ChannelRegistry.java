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

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Thread-safe registry for channels owned by a transport.
 *
 * <p>The registry keeps channel storage separate from channel selection. Selection receives a
 * snapshot of current channels so callers can add or remove channels while the selector keeps
 * only its own cursor state.</p>
 *
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
            // Snapshot before selecting so concurrent registry updates do not affect one selection pass.
            return selector.next(List.copyOf(registry.channels.values()));
        }
    }
}
