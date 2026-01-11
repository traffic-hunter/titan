package org.traffichunter.titan.core.channel;

import lombok.extern.slf4j.Slf4j;
import org.traffichunter.titan.core.util.buffer.Buffer;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public final class ChannelChain {

    private final Channel channel;

    private final List<ChannelInBoundHandler> inBoundFilters = new ArrayList<>();
    private final List<ChannelOutBoundHandler> outBoundFilters = new ArrayList<>();

    public ChannelChain(Channel channel) {
        this.channel = channel;
    }

    public ChannelChain add(ChannelInBoundHandler filter) {
        inBoundFilters.add(filter);
        return this;
    }

    public ChannelChain add(ChannelInBoundHandler... filters) {
        inBoundFilters.addAll(List.of(filters));
        return this;
    }

    public ChannelChain add(ChannelOutBoundHandler filter) {
        outBoundFilters.add(filter);
        return this;
    }

    public ChannelChain add(ChannelOutBoundHandler... filters) {
        outBoundFilters.addAll(List.of(filters));
        return this;
    }

    void processChannelConnecting(NetChannel channel) {
        try {
            inBoundFilters.forEach(
                    filter -> filter.sparkChannelConnecting(channel)
            );
        } catch (Exception e) {
            channel.close();
        }
    }

    void processChannelAfterConnected(NetChannel channel) {
        try {
            inBoundFilters.forEach(
                    filter -> filter.sparkChannelAfterConnected(channel)
            );
        } catch (Exception e) {
            channel.close();
        }
    }

    void processChannelRead(NetChannel channel) {
        try {
            inBoundFilters.forEach(
                    filter -> filter.sparkChannelRead(channel, Buffer.alloc(4096))
            );
        } catch (Exception e) {
            channel.close();
        }
    }

    void processChannelWrite(NetChannel channel) {
        try {
            outBoundFilters.forEach(
                    filter -> filter.sparkChannelWrite(channel, Buffer.alloc(4096))
            );
        } catch (Exception e) {
            channel.close();
        }
    }
}
