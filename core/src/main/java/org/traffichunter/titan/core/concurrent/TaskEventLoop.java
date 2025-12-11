package org.traffichunter.titan.core.concurrent;

import org.traffichunter.titan.core.util.event.EventLoopConstants;

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
            final Runnable task = takeTask();
            if(task != null) {
                task.run();
            }
        }
    }

    @Override
    protected void cleanUp() {
        // NOOP
    }
}
