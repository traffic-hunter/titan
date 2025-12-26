package org.traffichunter.titan.core.channel;

import java.util.List;

public final class ChannelInboundFilterChain extends AbstractFilterChain {

    private final List<ChannelInBoundFilter> filters;
    private int index = 0;

    public ChannelInboundFilterChain(ChannelInBoundFilter... filters) {
        this(List.of(filters));
    }

    public ChannelInboundFilterChain(List<ChannelInBoundFilter> filters) {
        this.filters = filters;
    }

    @Override
    public void doFilter(NetChannel channel) throws Exception {
        if(index >= filters.size()) {
            return;
        }

        filters.get(index++).doFilter(channel, this);
    }

    @Override
    void process(NetChannel context) throws Exception {
        if(filters.isEmpty()) {
            return;
        }

        for(ChannelInBoundFilter filter : filters) {
            filter.doFilter(context, this);
        }
    }
}
