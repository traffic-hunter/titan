package org.traffichunter.titan.core.concurrent;

public interface EventLoopLifeCycle {

    boolean isNotStarted();

    boolean isStarted();

    boolean isShuttingDown();

    boolean isShutdown();

    boolean isTerminated();
}