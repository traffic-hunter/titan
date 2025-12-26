package org.traffichunter.titan.core.channel;

public interface ChannelInBoundFilter {

    void doFilter(NetChannel channel, ChannelInboundFilterChain chain) throws Exception;
}
