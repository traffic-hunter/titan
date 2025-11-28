package org.traffichunter.titan.core.event;

import lombok.Getter;

@Getter
enum EventLoopStatus {

    NOT_STARTED(1),
    STARTED(2),
    SHUTTING_DOWN(3),
    SHUTDOWN(4),
    TERMINATED(5),
    ;

    private final int value;

    EventLoopStatus(int value) {
        this.value = value;
    }
}