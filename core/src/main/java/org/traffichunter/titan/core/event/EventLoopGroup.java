package org.traffichunter.titan.core.event;

public interface EventLoopGroup<E extends EventLoop> extends EventLoop {

    E next();
}
