package org.traffichunter.titan.core.channel;

import org.traffichunter.titan.core.util.Noop;

/**
 * @author yun
 */
public class TaskEventLoop extends SingleThreadEventLoop {

    public TaskEventLoop() {
        this(EventLoopConstants.TASK_EVENT_LOOP_THREAD_NAME);
    }

    public TaskEventLoop(final String eventLoopName) {
        super(eventLoopName);
    }

    @Override
    protected void doRun() {
        if(!inEventLoop()) {
            throw new IllegalStateException("Event loop is not in event loop");
        }

        while (!checkShutdown()) {
            final Runnable task;
            try {
                task = takeTask();
            } catch (InterruptedException e) {
                // shutdownNow() interrupts the wait; the loop condition decides whether to stop.
                continue;
            }
            task.run();
        }
    }

    @Noop
    @Override
    protected void cleanUp() {
        // NOOP
    }
}
