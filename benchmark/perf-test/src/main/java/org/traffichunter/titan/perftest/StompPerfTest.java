/*
The MIT License

Copyright (c) 2025 traffic-hunter

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
*/
package org.traffichunter.titan.perftest;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import org.traffichunter.titan.client.TitanClient;
import org.traffichunter.titan.core.transport.stomp.option.StompSessionOption;
import org.traffichunter.titan.core.util.buffer.Buffer;

/**
 * Measures delivery through real {@link TitanClient} producer and consumer connections.
 *
 * @author yun
 */
final class StompPerfTest {

    PerfTestReport run(PerfTestOptions options) throws Exception {
        TitanClient consumer = newClient(options);
        List<TitanClient> producers = new ArrayList<>(options.producers());
        ExecutorService executor = Executors.newFixedThreadPool(options.producers());
        CountDownLatch warmupCompleted = new CountDownLatch(options.warmupMessages());
        CountDownLatch completed = new CountDownLatch(options.messages());
        CountDownLatch producersCompleted = new CountDownLatch(options.producers());
        AtomicInteger sent = new AtomicInteger();
        AtomicInteger received = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        AtomicInteger sequence = new AtomicInteger();
        AtomicIntegerArray delivered = new AtomicIntegerArray(options.messages());
        long[] latencies = new long[options.messages()];
        long runId = ThreadLocalRandom.current().nextLong();

        try {
            consumer.start();
            consumer.connect().get(options.connectTimeout().toMillis(), TimeUnit.MILLISECONDS);
            consumer.subscribe(options.destination(), frame -> {
                byte[] body = frame.body();
                if (body.length < Long.BYTES + Integer.BYTES + Long.BYTES) {
                    return;
                }

                ByteBuffer payload = ByteBuffer.wrap(body);
                if (payload.getLong() != runId) {
                    return;
                }
                int id = payload.getInt();
                if (id < 0) {
                    warmupCompleted.countDown();
                    return;
                }
                if (id >= latencies.length) {
                    return;
                }
                if (!delivered.compareAndSet(id, 0, 1)) {
                    return;
                }

                latencies[id] = System.nanoTime() - payload.getLong();
                received.incrementAndGet();
                completed.countDown();
            }).get(options.connectTimeout().toMillis(), TimeUnit.MILLISECONDS);

            for (int index = 0; index < options.producers(); index++) {
                TitanClient producer = newClient(options);
                producer.start();
                producer.connect().get(options.connectTimeout().toMillis(), TimeUnit.MILLISECONDS);
                producers.add(producer);
            }

            for (int index = 0; index < options.warmupMessages(); index++) {
                byte[] payload = payload(options.payloadBytes(), runId, -1);
                producers.get(index % producers.size())
                        .send(options.destination(), Buffer.heap().alloc(payload))
                        .get(options.completionTimeout().toMillis(), TimeUnit.MILLISECONDS);
            }
            if (!warmupCompleted.await(options.completionTimeout().toMillis(), TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("Warm-up messages did not complete before the timeout");
            }

            long startedAt = System.nanoTime();
            long deadline = startedAt + options.completionTimeout().toNanos();
            for (TitanClient producer : producers) {
                executor.execute(() -> {
                    try {
                        while (true) {
                            int id = sequence.getAndIncrement();
                            if (id >= options.messages()) {
                                return;
                            }

                            try {
                                producer.send(
                                        options.destination(),
                                        Buffer.heap().alloc(payload(options.payloadBytes(), runId, id))
                                ).get(options.completionTimeout().toMillis(), TimeUnit.MILLISECONDS);
                                sent.incrementAndGet();
                            } catch (Exception error) {
                                failed.incrementAndGet();
                                completed.countDown();
                            }
                        }
                    } finally {
                        producersCompleted.countDown();
                    }
                });
            }

            boolean producersFinished = producersCompleted.await(
                    options.completionTimeout().toNanos(),
                    TimeUnit.NANOSECONDS
            );
            long remainingNanos = Math.max(0, deadline - System.nanoTime());
            boolean deliveriesFinished = completed.await(remainingNanos, TimeUnit.NANOSECONDS);
            long elapsedNanos = System.nanoTime() - startedAt;
            if (!producersFinished || !deliveriesFinished) {
                failed.addAndGet((int) completed.getCount());
            }

            return new PerfTestReport(
                    options.messages(),
                    sent.get(),
                    received.get(),
                    failed.get(),
                    elapsedNanos,
                    latencies
            );
        } finally {
            executor.shutdownNow();
            Duration timeout = options.connectTimeout();
            for (TitanClient producer : producers) {
                producer.shutdown(timeout.toMillis(), TimeUnit.MILLISECONDS);
            }
            consumer.shutdown(timeout.toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    private static byte[] payload(int size, long runId, int id) {
        byte[] payload = new byte[size];
        ByteBuffer.wrap(payload)
                .putLong(runId)
                .putInt(id)
                .putLong(System.nanoTime());
        return payload;
    }

    private static TitanClient newClient(PerfTestOptions options) {
        return TitanClient.builder()
                .host(options.host())
                .port(options.port())
                .worker(1)
                .connectTimeout(options.connectTimeout())
                .session(StompSessionOption.builder().heartbeatX(0L).heartbeatY(0L).build())
                .build();
    }
}
