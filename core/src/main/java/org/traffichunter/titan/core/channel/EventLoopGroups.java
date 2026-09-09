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

import java.util.concurrent.TimeUnit;

/**
 * Pair of event-loop groups used by network transports.
 *
 * <p>The primary group owns server accept channels. The secondary group owns accepted or
 * outbound {@link NetChannel} instances that perform reads and writes. Keeping these roles
 * separate prevents accept readiness from competing with regular connection I/O.</p>
 *
 * @param primaryGroup event loops responsible for accepting server connections
 * @param secondaryGroup event loops responsible for connected channel I/O and queued tasks
 * @author yun
 */
public record EventLoopGroups(
        ChannelPrimaryIOEventLoopGroup primaryGroup,
        ChannelSecondaryIOEventLoopGroup secondaryGroup
) {

    /**
     * Creates a server-capable group using the default primary and secondary loop counts.
     *
     * @return a group capable of accepting connections and processing channel I/O
     */
    public static EventLoopGroups group() {
        return new EventLoopGroups(
                new ChannelPrimaryIOEventLoopGroup(),
                new ChannelSecondaryIOEventLoopGroup()
        );
    }

    /**
     * Creates a client worker group with no primary acceptor loops.
     *
     * <p>Use this overload for outbound clients. A group created here can register
     * {@link NetChannel} instances but cannot register a {@link NetServerChannel}, because its
     * primary group intentionally contains zero event loops.</p>
     *
     * @param secondary number of channel I/O workers
     * @return a client-only event-loop group
     */
    public static EventLoopGroups group(int secondary) {
        return new EventLoopGroups(
                new ChannelPrimaryIOEventLoopGroup(0),
                new ChannelSecondaryIOEventLoopGroup(secondary)
        );
    }

    /**
     * Creates a server-capable group with explicit acceptor and worker counts.
     *
     * @param primary   number of threads for the primary event loop
     * @param secondary number of threads for the secondary event loop
     * @return a group with separate acceptor and channel I/O workers
     */
    public static EventLoopGroups group(int primary, int secondary) {
        return new EventLoopGroups(
                new ChannelPrimaryIOEventLoopGroup(primary),
                new ChannelSecondaryIOEventLoopGroup(secondary)
        );
    }

    /** Creates a server-capable group with one primary and one secondary event loop. */
    public static EventLoopGroups singleGroup() {
        return group(1, 1);
    }

    /** Starts every configured primary and secondary event loop. */
    public void start() {
        primaryGroup.start();
        secondaryGroup.start();
    }

    /** Returns whether all configured event-loop groups have started. */
    public boolean isActive() {
        return primaryGroup.isStarted() && secondaryGroup.isStarted();
    }

    /** Returns whether both groups are shutting down. */
    public boolean isShuttingDown() {
        return primaryGroup.isShuttingDown() && secondaryGroup.isShuttingDown();
    }

    /** Returns whether both groups have completed shutdown. */
    public boolean isShutdown() {
        return primaryGroup.isShutdown() && secondaryGroup.isShutdown();
    }

    /**
     * Requests graceful shutdown from both groups using the same deadline.
     *
     * @param timeout maximum drain duration per group
     * @param unit unit of {@code timeout}
     */
    public void gracefullyShutdown(long timeout, TimeUnit unit) {
        primaryGroup.gracefullyShutdown(timeout, unit);
        secondaryGroup.gracefullyShutdown(timeout, unit);
    }

    /** Requests graceful shutdown using each event-loop group's default timeout. */
    public void gracefullyShutdown() {
        primaryGroup.gracefullyShutdown();
        secondaryGroup.gracefullyShutdown();
    }

    /**
     * Routes server channels to the primary group and connected channels to the secondary group.
     *
     * @throws java.util.NoSuchElementException if a server channel is registered with a client-only group
     */
    public void register(Channel channel) {
        if(channel instanceof NetChannel netChannel) {
            secondaryGroup.register(netChannel);
        } else if(channel instanceof NetServerChannel serverChannel) {
            primaryGroup.register(serverChannel);
        }
    }
}
