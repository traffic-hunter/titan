package org.traffichunter.titan.core.message.dispatcher;

/**
 * Result of an operational dispatcher queue deletion request.
 *
 * @param status deletion outcome
 * @param size queue size observed when the deletion request was evaluated
 * @author yungwang-o
 */
public record DispatcherQueueDeleteResult(
        Status status,
        int size
) {

    /**
     * Deletion status values used by management APIs to map domain outcomes to
     * transport-specific responses.
     */
    public enum Status {
        /**
         * The queue was removed.
         */
        DELETED,
        /**
         * No queue exists for the requested destination.
         */
        NOT_FOUND,
        /**
         * The queue still contains messages and force deletion was not allowed.
         */
        NOT_EMPTY
    }

    /**
     * Returns whether the deletion request removed a queue.
     */
    public boolean isDeleted() {
        return status == Status.DELETED;
    }
}
