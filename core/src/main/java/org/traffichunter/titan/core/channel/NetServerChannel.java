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
import org.traffichunter.titan.core.util.concurrent.Promise;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketOption;

/**
 * Listening server socket channel.
 *
 * <p>A server channel is registered for accept readiness on the primary event loop. Each
 * successful {@link #accept()} creates a separate {@link NetChannel}; that child channel is
 * then initialized and registered on a secondary event loop for read/write processing.</p>
 *
 * @author yun
 */
public interface NetServerChannel extends Channel {

    static NetServerChannel open(ChannelHandShakeEventListener initializer) throws IOException {
        return new NewIONetServerChannel(initializer);
    }

    @Override
    <T> NetServerChannel setOption(SocketOption<T> option, T value);

    Promise<Void> bind(String host, int port);

    /**
     * Returns raw server transport operations that bypass channel-level orchestration.
     */
    Internal internal();

    /**
     * Binds the listening socket to the given address.
     */
    Promise<Void> bind(InetSocketAddress address);

    /**
     * Accepts one pending child connection, or returns {@code null} when no connection is ready.
     */
    Promise<NetChannel> accept();

    /**
     * Raw listening-socket operations used by Titan's server transport.
     *
     * <p>These operations do not schedule work or propagate channel pipeline events.</p>
     */
    interface Internal {

        void bind(InetSocketAddress address) throws IOException;

        @Nullable NetChannel accept();
    }
}
