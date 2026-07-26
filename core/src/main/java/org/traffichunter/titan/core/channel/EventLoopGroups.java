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
 * Pair of event-loop groups used by network transports.
 *
 * <p>The primary group owns server accept channels. The secondary group owns accepted or
 * outbound {@link NetChannel} instances that perform reads and writes. Keeping these roles
 * separate prevents accept readiness from competing with regular connection I/O.</p>
 *
 * @author yun
 */
public record EventLoopGroups(
        ChannelPrimaryIOEventLoopGroup primaryGroup,
        ChannelSecondaryIOEventLoopGroup secondaryGroup
) {

    public static EventLoopGroups group() {
        return new EventLoopGroups(
                new ChannelPrimaryIOEventLoopGroup(),
                new ChannelSecondaryIOEventLoopGroup()
        );
    }

    public static EventLoopGroups group(int primary) {
        return new EventLoopGroups(
                new ChannelPrimaryIOEventLoopGroup(primary),
                new ChannelSecondaryIOEventLoopGroup()
        );
    }

    /**
     * @param primary   number of threads for the primary event loop
     * @param secondary number of threads for the secondary event loop
     */
    public static EventLoopGroups group(int primary, int secondary) {
        return new EventLoopGroups(
                new ChannelPrimaryIOEventLoopGroup(primary),
                new ChannelSecondaryIOEventLoopGroup(secondary)
        );
    }

    public static EventLoopGroups singleGroup() {
        return group(1, 1);
    }

    public void start() {
        primaryGroup.start();
        secondaryGroup.start();
    }

    public boolean isActive() {
        return primaryGroup.isStarted() && secondaryGroup.isStarted();
    }

    public boolean isShuttingDown() {
        return primaryGroup.isShuttingDown() && secondaryGroup.isShuttingDown();
    }

    public boolean isShutdown() {
        return primaryGroup.isShutdown() && secondaryGroup.isShutdown();
    }

    public void gracefullyShutdown(long timeout, TimeUnit unit) {
        primaryGroup.gracefullyShutdown(timeout, unit);
        secondaryGroup.gracefullyShutdown(timeout, unit);
    }

    public void gracefullyShutdown() {
        primaryGroup.gracefullyShutdown();
        secondaryGroup.gracefullyShutdown();
    }

    public void register(Channel channel) {
        if(channel instanceof NetChannel netChannel) {
            secondaryGroup.register(netChannel);
        } else if(channel instanceof NetServerChannel serverChannel) {
            primaryGroup.register(serverChannel);
        }
    }
}
