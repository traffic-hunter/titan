package org.traffichunter.titan.core.channel;

public interface EventLoopGroup<E extends EventLoop> extends EventLoop {

    E next();
}
