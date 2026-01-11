package org.traffichunter.titan.core.channel;

public abstract class AbstractFilterChain {

    public abstract void doFilter(NetChannel context) throws Exception;

    abstract void process(NetChannel context) throws Exception;
}
