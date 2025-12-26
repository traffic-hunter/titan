package org.traffichunter.titan.core.channel;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public final class ChannelChain {

    private final List<ChannelInBoundFilter> acceptListenerFilters = new ArrayList<>();
    private final List<ChannelInBoundFilter> inBoundFilters = new ArrayList<>();
    private final List<ChannelOutBoundFilter> outBoundFilters = new ArrayList<>();

    public ChannelChain add(ChannelInBoundFilter filter) {
        inBoundFilters.add(filter);
        return this;
    }

    public ChannelChain add(ChannelInBoundFilter... filters) {
        inBoundFilters.addAll(List.of(filters));
        return this;
    }

    public ChannelChain add(ChannelOutBoundFilter filter) {
        outBoundFilters.add(filter);
        return this;
    }

    public ChannelChain add(ChannelOutBoundFilter... filters) {
        outBoundFilters.addAll(List.of(filters));
        return this;
    }

    public ChannelChain accept(ChannelInBoundFilter filter) {
        acceptListenerFilters.add(filter);
        return this;
    }

    void fireAcceptListener(NetChannel channel) {
        try {
            ChannelInboundFilterChain chain = new ChannelInboundFilterChain(acceptListenerFilters);
            chain.process(channel);
        } catch (Exception e) {
            channel.close();
        }
    }

    void fireInboundChannel(NetChannel channel) {
        try {
            ChannelInboundFilterChain chain = new ChannelInboundFilterChain(inBoundFilters);
            chain.process(channel);
        } catch (Exception e) {
            channel.close();
        }
    }

    void fireOutboundChannel(NetChannel channel) {
        try {
            ChannelOutBoundFilterChain chain = new ChannelOutBoundFilterChain(outBoundFilters);
            chain.process(channel);
        } catch (Exception e) {
            channel.close();
        }
    }
}
