package org.traffichunter.titan.core.channel;

public interface ChannelOutBoundFilter {

    void doFilter(Context context, ChannelOutBoundFilterChain chain) throws Exception;
}
