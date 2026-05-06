package org.traffichunter.titan.core.channel;

import lombok.Getter;

/**
 * Ordered lifecycle states for event-loop shutdown checks.
 *
 * <p>The enum order is significant; code compares states to determine whether a loop has
 * reached or passed a shutdown phase.</p>
 */
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
