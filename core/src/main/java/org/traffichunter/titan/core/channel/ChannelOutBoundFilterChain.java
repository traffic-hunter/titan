package org.traffichunter.titan.core.channel;

import java.util.List;

public final class ChannelOutBoundFilterChain extends AbstractFilterChain {

    private final List<ChannelOutBoundFilter> filters;
    private int index = 0;

    public ChannelOutBoundFilterChain(ChannelOutBoundFilter... filters) {
        this(List.of(filters));
    }

    public ChannelOutBoundFilterChain(List<ChannelOutBoundFilter> filters) {
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
    void process(NetChannel channel) throws Exception {
        if(filters.isEmpty()) {
            return;
        }

        for(ChannelOutBoundFilter filter : filters) {
            filter.doFilter(channel, this);
        }
    }
}
