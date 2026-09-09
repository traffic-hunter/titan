/*
 * Copyright 2025 traffic-hunter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traffichunter.titan.core.channel;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traffichunter.titan.core.util.Assert;

/**
 * Single-threaded event loop that combines queued tasks with Java NIO selector polling.
 *
 * <p>All selector mutations are funneled through this event-loop thread. External callers
 * enqueue registration tasks, and {@link #addTask(Runnable)} wakes the selector so those
 * tasks can run promptly even when the loop is blocked in {@code select()}.</p>
 *
 * @author yungwang-o
 */
public abstract class SingleThreadIOEventLoop extends SingleThreadEventLoop implements IOEventLoop {

    private static final Logger log = LoggerFactory.getLogger(SingleThreadIOEventLoop.class);

    private final IOSelector ioSelector;

    public SingleThreadIOEventLoop(final String eventLoopName) {
        super(eventLoopName, new ConcurrentLinkedQueue<>());
        this.ioSelector = IOSelector.open();
    }

    @Override
    public void register(Channel channel) {
        channel.register(this);
    }

    @Override
    protected final void addTask(final Runnable task) {
        super.addTask(task);
        wakeUp();
    }

    @Override
    public boolean inEventLoop(final Thread thread) {
        return this.thread == thread;
    }

    @Override
    protected void doRun() throws IOException {
        Assert.checkState(inEventLoop(), "Event loop is not in event loop");
        Assert.checkState(ioSelector.isOpen(), "IOHandler is not open");

        log.info("Event loop start!!");

        while (!checkShutdown()) {
            runAllTasks();

            long delayNanos = delayNanosUntilNextScheduledTask();
            int ioEventCnt;
            if (delayNanos < 0) {
                ioEventCnt = ioSelector.invokeEvent();
            } else if (delayNanos <= 0) {
                ioEventCnt = ioSelector.invokeNowEvent();
            } else {
                long timeoutMillis = Math.max(1L, TimeUnit.NANOSECONDS.toMillis(delayNanos));
                ioEventCnt = ioSelector.invokeEvent(timeoutMillis);
            }

            if(ioEventCnt == 0) {
                continue;
            }

            runAllTasks();

            processIO(ioSelector.readyIOEvents());
        }
    }

    /**
     * Processes ready selector keys after queued tasks have had a chance to run.
     */
    protected abstract void processIO(Set<SelectionKey> keySet);

    @Override
    protected void cleanUp() {
        try {
            ioSelector.close();
        } catch (IOException e) {
            log.error("Failed to close selector: {}", e.getMessage());
        }
    }

    @Override
    public IOSelector ioSelector() {
        return ioSelector;
    }

    @Deprecated(forRemoval = true)
    private void processPendingTasks() {
        Runnable task;

        while ((task = taskQueue.poll()) != null) {
            try {
                task.run();
            } catch (Exception e) {
                log.error("Error processing task: {}", e.getMessage());
            }
        }
    }

    @Override
    void wakeUp() {
        ioSelector.wakeUp();
    }
}
