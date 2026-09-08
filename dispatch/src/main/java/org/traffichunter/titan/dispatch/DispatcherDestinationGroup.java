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
package org.traffichunter.titan.dispatch;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.util.Destination;
import org.traffichunter.titan.core.util.IdGenerator;

/**
 * Group backed by its own {@link TrieDispatcher}.
 *
 * <p>Reads use the local dispatcher. Creation and removal go through the registry so the
 * ownership index stays in sync. The {@code *Local} methods access the local dispatcher
 * directly. Only the registry calls them.</p>
 *
 * @author yun
 */
final class DispatcherDestinationGroup implements DestinationGroup {

    private final String id;
    private final String name;
    private final DestinationGroupRegistry registry;
    private final Dispatcher dispatcher;

    DispatcherDestinationGroup(
            String name,
            DestinationGroupRegistry registry,
            long defaultMaxPendingBytes,
            long defaultResumePendingBytes
    ) {
        this.id = IdGenerator.uuid();
        this.name = name;
        this.registry = registry;
        this.dispatcher = new TrieDispatcher(defaultMaxPendingBytes, defaultResumePendingBytes);
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public @Nullable DispatcherQueue get(Destination destination) {
        return getLocal(destination);
    }

    @Override
    public DispatcherQueue getOrPut(Destination destination) {
        return registry.register(this, destination, null);
    }

    @Override
    public DispatcherQueue getOrPut(Destination destination, long maxPendingBytes) {
        return registry.register(this, destination, maxPendingBytes);
    }

    @Override
    public List<DispatcherQueue> searchAll(Destination destination) {
        return searchAllLocal(destination);
    }

    @Override
    public boolean exists(Destination destination) {
        return existsLocal(destination);
    }

    @Override
    public void remove(Destination destination) {
        registry.removeFrom(this, destination);
    }

    @Nullable DispatcherQueue getLocal(Destination destination) {
        return dispatcher.get(destination);
    }

    DispatcherQueue getOrPutLocal(Destination destination, @Nullable Long maxPendingBytes) {
        return maxPendingBytes == null
                ? dispatcher.getOrPut(destination)
                : dispatcher.getOrPut(destination, maxPendingBytes);
    }

    List<DispatcherQueue> searchAllLocal(Destination destination) {
        return dispatcher.searchAll(destination);
    }

    boolean existsLocal(Destination destination) {
        return dispatcher.exists(destination);
    }

    void removeLocal(Destination destination) {
        dispatcher.remove(destination);
    }

    @Override
    public String toString() {
        return "DestinationGroup[" + name + "]";
    }
}
