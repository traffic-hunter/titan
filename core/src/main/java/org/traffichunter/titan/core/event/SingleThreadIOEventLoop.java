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
package org.traffichunter.titan.core.event;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.traffichunter.titan.core.util.Assert;
import org.traffichunter.titan.core.util.Time;

/**
 * @author yungwang-o
 */
@Slf4j
public abstract class SingleThreadIOEventLoop extends SingleThreadEventLoop implements IOEventLoop {

    protected final Selector selector;

    public SingleThreadIOEventLoop(final String eventLoopName) {
        super(eventLoopName, new ConcurrentLinkedQueue<>());
        try {
            this.selector = Selector.open();
        } catch (IOException e) {
            throw new EventLoopException("Select open error!!", e);
        }
    }

    protected final void addTask(final Runnable task) {
        Assert.checkNull(task, "task is null");
        if(isShuttingDown()) {
            throw new RejectedExecutionException("Event loop is shutdown!!");
        }

        if(!taskQueue.offer(task)) {
            throw new RejectedExecutionException("Failed to add task!!");
        }
    }

    @Override
    public boolean inEventLoop(final Thread thread) {
        return this.thread == thread;
    }

    @Override
    protected void doRun() throws IOException {
        if (!inEventLoop()) {
            throw new IllegalStateException("Event loop is not in event loop");
        }
        if(!selector.isOpen()) {
            throw new IllegalStateException("Selector is not open");
        }

        while (!checkShutdown()) {
            runAllTasks();

            int selected = selector.select();
            if(selected == 0) {
                continue;
            }

            runAllTasks();

            handleIO(selector.selectedKeys());
        }
    }

    protected abstract void handleIO(Set<SelectionKey> keySet);

    @Override
    protected void cleanUp() {
        try {
            selector.close();
        } catch (IOException e) {
            log.error("Failed to close selector: {}", e.getMessage());
        }
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

    @CanIgnoreReturnValue
    private int runAllTasks() {
        int count = 0;
        while (true) {
            Runnable task = taskQueue.poll();
            if(task == null) {
                break;
            }

            try {
                count++;
                task.run();
            } catch (Exception e) {
                log.error("Failed to run task! = {}", e.getMessage());
            }
        }

        return count;
    }
}
