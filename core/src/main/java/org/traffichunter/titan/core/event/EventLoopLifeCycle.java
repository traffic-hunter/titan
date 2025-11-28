package org.traffichunter.titan.core.event;

public interface EventLoopLifeCycle {

    boolean isNotStarted();

    boolean isStarted();

    boolean isShuttingDown();

    boolean isShutdown();

    boolean isTerminated();
}