package org.traffichunter.titan.core.channel;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class ChannelChain {

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

    void fireInboundChannel(Context context) {
        try {
            ChannelInboundFilterChain chain = new ChannelInboundFilterChain(inBoundFilters);
            chain.process(context);
        } catch (Exception e) {
            try {
                context.close();
            } catch (IOException ignored) { }
        }
    }

    void fireOutboundChannel(Context context) {
        try {
            ChannelOutBoundFilterChain chain = new ChannelOutBoundFilterChain(outBoundFilters);
            chain.process(context);
        } catch (Exception e) {
            try {
                context.close();
            } catch (IOException ignored) { }
        }
    }
}
