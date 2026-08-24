package org.traffichunter.titan.core.channel;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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

    @Override
    default <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
            throws InterruptedException {
        rejectBulkInvocationFromEventLoop("invokeAll");
        return next().invokeAll(tasks);
    }

    @Override
    default <T> List<Future<T>> invokeAll(
            Collection<? extends Callable<T>> tasks,
            long timeout,
            TimeUnit unit
    ) throws InterruptedException {
        rejectBulkInvocationFromEventLoop("invokeAll");
        return next().invokeAll(tasks, timeout, unit);
    }

    @Override
    default <T> T invokeAny(Collection<? extends Callable<T>> tasks)
            throws InterruptedException, ExecutionException {
        rejectBulkInvocationFromEventLoop("invokeAny");
        return next().invokeAny(tasks);
    }

    @Override
    default <T> T invokeAny(
            Collection<? extends Callable<T>> tasks,
            long timeout,
            TimeUnit unit
    ) throws InterruptedException, ExecutionException, TimeoutException {
        rejectBulkInvocationFromEventLoop("invokeAny");
        return next().invokeAny(tasks, timeout, unit);
    }

    private void rejectBulkInvocationFromEventLoop(String operation) {
        if (inEventLoop()) {
            throw new RejectedExecutionException(
                    "Calling " + operation + " from within the event loop is not allowed"
            );
        }
    }
}
