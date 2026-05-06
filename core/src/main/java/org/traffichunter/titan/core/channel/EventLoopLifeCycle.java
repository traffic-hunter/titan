package org.traffichunter.titan.core.channel;

/**
 * Observable lifecycle state for event loops and event-loop groups.
 *
 * @author yun
 */
public interface EventLoopLifeCycle {

    boolean isNotStarted();

    boolean isStarted();

    boolean isShuttingDown();

    boolean isShutdown();

    boolean isTerminated();
}
