package org.traffichunter.titan.core.event;

enum EventLoopStatus {
    NOT_INITIALIZED,
    INITIALIZED,
    SUSPENDING,
    SUSPENDED,
    STARTING,
    STOPPING,
    STOPPED
}