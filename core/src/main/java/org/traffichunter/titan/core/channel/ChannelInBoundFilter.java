package org.traffichunter.titan.core.channel;

public interface ChannelInBoundFilter {

    void doFilter(Context context, ChannelInboundFilterChain chain) throws Exception;
}
