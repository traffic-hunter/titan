/*
 * The MIT License
 *
 * Copyright (c) 2025 traffic-hunter
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.traffichunter.titan.core.concurrent;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import lombok.extern.slf4j.Slf4j;
import org.traffichunter.titan.core.util.Assert;

/**
 * @author yungwang-o
 */
@Slf4j
public abstract class SingleThreadIOEventLoop extends SingleThreadEventLoop implements IOEventLoop {

    private final IOSelector ioSelector;

    public SingleThreadIOEventLoop(final String eventLoopName) {
        super(eventLoopName, new ConcurrentLinkedQueue<>());
        this.ioSelector = IOSelector.open();
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

            int ioEventCnt = ioSelector.invokeEvent();
            if(ioEventCnt == 0) {
                continue;
            }

            runAllTasks();

            processIO(ioSelector.readyIOEvents());
        }
    }

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
    public IOSelector ioHandler() {
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
