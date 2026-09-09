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

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Set;

/**
 * Thin wrapper around Java NIO {@link Selector}.
 *
 * <p>The wrapper keeps selector registration code local to the channel package and exposes
 * domain-specific operations such as accept, read, write, and connect readiness. Registered
 * channels attach themselves as the selection key attachment so event loops can dispatch
 * ready keys without additional lookup tables.</p>
 *
 * @author yun
 */
public final class IOSelector {

    private static final Logger log = LoggerFactory.getLogger(IOSelector.class);

    private final Selector selector;

    private IOSelector(Selector selector) {
        this.selector = selector;
    }

    static IOSelector open() {
        try {
            return new IOSelector(Selector.open());
        } catch (IOException e) {
            throw new NewIOException("Selector is not open", e);
        }
    }

    boolean isOpen() {
        return selector.isOpen();
    }

    int invokeEvent() throws IOException {
        return selector.select();
    }

    int invokeEvent(long timeout) throws IOException {
        return selector.select(timeout);
    }

    int invokeNowEvent() throws IOException {
        return selector.selectNow();
    }

    Set<SelectionKey> readyIOEvents() {
        return selector.selectedKeys();
    }

    void wakeUp() {
        selector.wakeup();
    }

    void close() throws IOException {
        selector.close();
    }

    @CanIgnoreReturnValue
    public IOSelector registerAccept(NetServerChannel channel) throws IOException {
        return registerOps(channel, SelectionKey.OP_ACCEPT);
    }

    @CanIgnoreReturnValue
    public IOSelector unregisterAccept(NetServerChannel channel) throws IOException {
        return unregisterOps(channel, SelectionKey.OP_ACCEPT);
    }

    @CanIgnoreReturnValue
    public IOSelector registerRead(NetChannel channel) throws IOException {
        return registerOps(channel, SelectionKey.OP_READ);
    }

    @CanIgnoreReturnValue
    public IOSelector unregisterRead(NetServerChannel channel) throws IOException {
        return unregisterOps(channel, SelectionKey.OP_READ);
    }

    @CanIgnoreReturnValue
    public IOSelector registerWrite(NetChannel channel) throws IOException {
        return registerOps(channel, SelectionKey.OP_WRITE);
    }

    @CanIgnoreReturnValue
    public IOSelector unregisterWrite(NetChannel channel) throws IOException {
        return unregisterOps(channel, SelectionKey.OP_WRITE);
    }

    @CanIgnoreReturnValue
    public IOSelector registerConnect(NetChannel channel) throws IOException {
        return registerOps(channel, SelectionKey.OP_CONNECT);
    }

    @CanIgnoreReturnValue
    public IOSelector unregisterConnect(NetChannel channel) throws IOException {
        return unregisterOps(channel, SelectionKey.OP_CONNECT);
    }

    @CanIgnoreReturnValue
    public IOSelector registerOps(Channel channel, int ops) throws IOException {
        return registerOps(channel, ops, channel);
    }

    @CanIgnoreReturnValue
    public IOSelector unregisterOps(Channel channel, int ops) throws IOException {
        if(channel instanceof AbstractChannel abstractChannel) {
            unregisterOps(abstractChannel.selectableChannel(), ops);
        }
        return this;
    }

    @CanIgnoreReturnValue
    public IOSelector registerOps(Channel channel, int ops, Object attachment) throws IOException {
        if(channel instanceof AbstractChannel abstractChannel) {
            registerOps(abstractChannel.selectableChannel(), ops, attachment);
        }

        return this;
    }

    @CanIgnoreReturnValue
    IOSelector registerOps(SelectableChannel channel, int ops, Object attachment) throws IOException {
        SelectionKey key = channel.keyFor(selector);

        if(key == null) {
            channel.register(selector, ops, attachment);
        } else if(key.isValid()) {
            key.interestOpsOr(ops);
        }
        return this;
    }

    @CanIgnoreReturnValue
    IOSelector unregisterOps(SelectableChannel channel, int ops) {
        SelectionKey key = channel.keyFor(selector);
        if(key == null || !key.isValid()) {
            return this;
        }

        key.interestOpsAnd(~ops);
        return this;
    }
}
