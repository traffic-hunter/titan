package org.traffichunter.titan.core.channel;

public abstract class AbstractFilterChain {

    public abstract void doFilter(Context context) throws Exception;

    abstract void process(Context context) throws Exception;
}
