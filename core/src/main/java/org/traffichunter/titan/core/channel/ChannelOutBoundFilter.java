package org.traffichunter.titan.core.channel;

public interface ChannelOutBoundFilter {

    void doFilter(NetChannel context, ChannelOutBoundFilterChain chain) throws Exception;
}
