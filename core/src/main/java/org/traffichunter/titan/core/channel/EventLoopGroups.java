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
package org.traffichunter.titan.core.channel;

import java.util.concurrent.TimeUnit;

/**
 * Event-loop groups owned by network transports.
 *
 * <p>The primary group owns server accept channels. The secondary group owns accepted or
 * outbound {@link NetChannel} instances that perform reads and writes. The worker group runs
 * delegated work that must not block either I/O group, such as TLS engine tasks. Keeping these
 * roles separate prevents accept readiness and connection I/O from competing with blocking
 * work.</p>
 *
 * @author yun
 */
public record EventLoopGroups(
        ChannelPrimaryIOEventLoopGroup primaryGroup,
        ChannelSecondaryIOEventLoopGroup secondaryGroup,
        WorkerEventLoopGroup workerGroup
) {

    public EventLoopGroups(
            ChannelPrimaryIOEventLoopGroup primaryGroup,
            ChannelSecondaryIOEventLoopGroup secondaryGroup
    ) {
        this(primaryGroup, secondaryGroup, new WorkerEventLoopGroup());
    }

    public static EventLoopGroups group() {
        return new EventLoopGroups(
                new ChannelPrimaryIOEventLoopGroup(),
                new ChannelSecondaryIOEventLoopGroup(),
                new WorkerEventLoopGroup()
        );
    }

    public static EventLoopGroups group(int primary) {
        return new EventLoopGroups(
                new ChannelPrimaryIOEventLoopGroup(primary),
                new ChannelSecondaryIOEventLoopGroup(),
                new WorkerEventLoopGroup()
        );
    }

    /**
     * @param primary   number of threads for the primary event loop
     * @param secondary number of threads for the secondary event loop
     */
    public static EventLoopGroups group(int primary, int secondary) {
        return new EventLoopGroups(
                new ChannelPrimaryIOEventLoopGroup(primary),
                new ChannelSecondaryIOEventLoopGroup(secondary),
                new WorkerEventLoopGroup()
        );
    }

    /**
     * @param primary   number of threads for the primary event loop
     * @param secondary number of threads for the secondary event loop
     * @param worker    number of threads for delegated blocking work
     */
    public static EventLoopGroups group(int primary, int secondary, int worker) {
        return new EventLoopGroups(
                new ChannelPrimaryIOEventLoopGroup(primary),
                new ChannelSecondaryIOEventLoopGroup(secondary),
                new WorkerEventLoopGroup(worker)
        );
    }

    public static EventLoopGroups singleGroup() {
        return group(1, 1, 1);
    }

    public void start() {
        primaryGroup.start();
        secondaryGroup.start();
        workerGroup.start();
    }

    public boolean isActive() {
        return primaryGroup.isStarted() && secondaryGroup.isStarted() && workerGroup.isStarted();
    }

    public boolean isShuttingDown() {
        return primaryGroup.isShuttingDown()
                && secondaryGroup.isShuttingDown()
                && workerGroup.isShuttingDown();
    }

    public boolean isShutdown() {
        return primaryGroup.isShutdown() && secondaryGroup.isShutdown() && workerGroup.isShutdown();
    }

    public void gracefullyShutdown(long timeout, TimeUnit unit) {
        primaryGroup.gracefullyShutdown(timeout, unit);
        secondaryGroup.gracefullyShutdown(timeout, unit);
        workerGroup.gracefullyShutdown(timeout, unit);
    }

    public void gracefullyShutdown() {
        primaryGroup.gracefullyShutdown();
        secondaryGroup.gracefullyShutdown();
        workerGroup.gracefullyShutdown();
    }

    public void register(Channel channel) {
        if(channel instanceof NetChannel netChannel) {
            secondaryGroup.register(netChannel);
        } else if(channel instanceof NetServerChannel serverChannel) {
            primaryGroup.register(serverChannel);
        }
    }
}
