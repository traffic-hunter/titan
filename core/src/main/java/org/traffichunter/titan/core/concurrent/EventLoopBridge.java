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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

/**
 * @author yungwang-o
 */
@Slf4j
public final class EventLoopBridge<T> {

    private final BlockingQueue<T> bridge;

    public EventLoopBridge(final int capacity) {
        this.bridge = new LinkedBlockingQueue<>(capacity);
    }

    public void produce(final T task) {
        try {
            final boolean offer = bridge.offer(task, 10, TimeUnit.SECONDS);
            if (!offer) {
                throw new EventLoopException("Failed to produce event bridge");
            }
        } catch (InterruptedException ignore) {
            Thread.currentThread().interrupt();
        }
    }

    public T consume() {
        try {
            return bridge.take();
        } catch (InterruptedException ignore) {
            log.warn("Event bridge interrupted");
            return null;
        }
    }

    public List<T> pressure() {
        List<T> tmp = new ArrayList<>(bridge.size());
        bridge.drainTo(tmp);
        return tmp;
    }
}
