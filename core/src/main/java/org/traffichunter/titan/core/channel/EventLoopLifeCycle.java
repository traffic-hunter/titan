package org.traffichunter.titan.core.channel;

public interface EventLoopLifeCycle {

    boolean isNotStarted();

    boolean isStarted();

    boolean isShuttingDown();

    boolean isShutdown();

    boolean isTerminated();
}