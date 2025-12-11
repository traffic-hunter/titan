package org.traffichunter.titan.core.concurrent;

public interface EventLoopGroup<E extends EventLoop> extends EventLoop {

    E next();
}
