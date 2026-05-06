package org.traffichunter.titan.core.channel;

/**
 * Event-loop collection that can select a concrete loop for delegated work.
 *
 * @author yun
 */
public interface EventLoopGroup<E extends EventLoop> extends EventLoop {

    /**
     * Selects the next event loop, usually by round-robin.
     */
    E next();
}
