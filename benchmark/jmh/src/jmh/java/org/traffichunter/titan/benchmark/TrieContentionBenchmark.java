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
package org.traffichunter.titan.benchmark;

import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.traffichunter.titan.core.util.Destination;
import org.traffichunter.titan.dispatch.DispatcherQueue;
import org.traffichunter.titan.dispatch.TrieDispatcher;

/**
 * Many producers resolving queues that already exist on one shared dispatcher.
 * This is the routing hot path: every SEND calls {@code getOrPut} for a queue
 * that was created long ago.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class TrieContentionBenchmark {

    private TrieDispatcher dispatcher;
    private Destination[] keys;

    @Setup(Level.Trial)
    public void setUp() {
        dispatcher = new TrieDispatcher();
        keys = new Destination[64];
        for (int i = 0; i < keys.length; i++) {
            keys[i] = Destination.create("/topic/bench/" + i);
            dispatcher.getOrPut(keys[i]);
        }
    }

    @Benchmark
    @Threads(1)
    public DispatcherQueue getOrPutHitSingleThread() {
        return dispatcher.getOrPut(keys[(int) (Thread.currentThread().threadId() & 63)]);
    }

    @Benchmark
    @Threads(16)
    public DispatcherQueue getOrPutHitSixteenThreads() {
        return dispatcher.getOrPut(keys[(int) (Thread.currentThread().threadId() & 63)]);
    }
}
