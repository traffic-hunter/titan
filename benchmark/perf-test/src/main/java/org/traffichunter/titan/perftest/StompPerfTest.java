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
package org.traffichunter.titan.perftest;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.traffichunter.titan.client.ClientException;
import org.traffichunter.titan.client.TitanClient;
import org.traffichunter.titan.core.codec.stomp.StompFrames;
import org.traffichunter.titan.core.codec.stomp.StompHeaders.Elements;
import org.traffichunter.titan.core.transport.stomp.option.StompSessionOption;
import org.traffichunter.titan.core.util.buffer.Buffer;
import org.traffichunter.titan.perftest.MeasurementLedger.Reception;
import org.traffichunter.titan.perftest.PerfTestOptions.SendMode;
import org.traffichunter.titan.perftest.PerfTestOptions.Transport;

/**
 * Measures delivery through real {@link TitanClient} producer and consumer connections.
 *
 * <p>Connecting, subscribing, and the warm-up all finish before the producers are released
 * together, and from that moment every wait counts down one shared monotonic deadline. When the
 * deadline passes the producers stop, the runner joins them, and only then is the report frozen,
 * so nothing observed during the teardown can change the numbers.</p>
 *
 * @author yun
 */
final class StompPerfTest {

    /** Grace period for interrupted producer threads once the deadline has already passed. */
    private static final long JOIN_GRACE_MILLIS = 5_000;

    PerfTestReport run(PerfTestOptions options) throws Exception {
        long runId = ThreadLocalRandom.current().nextLong();
        MeasurementLedger ledger = new MeasurementLedger(options.messages(), options.warmupMessages());
        CountDownLatch warmupCompleted = new CountDownLatch(Math.max(options.warmupMessages(), 0));
        CountDownLatch delivered = new CountDownLatch(options.messages());
        CountDownLatch startBarrier = new CountDownLatch(1);
        AtomicInteger sequence = new AtomicInteger();
        AtomicLong deadlineNanos = new AtomicLong();
        List<String> cleanupErrors = Collections.synchronizedList(new ArrayList<>());
        List<TitanClient> producers = new ArrayList<>(options.producers());
        ExecutorService executor = Executors.newFixedThreadPool(options.producers());
        TitanClient consumer = newClient(options);

        try {
            consumer.start();
            consumer.connect().get(options.connectTimeout().toMillis(), TimeUnit.MILLISECONDS);
            // The SUBSCRIBE carries a receipt, so this future completes only once the broker has
            // registered the subscription. Publishing before that would race the first messages
            // against a subscription the broker does not hold yet.
            consumer.subscribe(
                    options.group(),
                    options.destination(),
                    Map.of(Elements.RECEIPT, receiptId(runId, "subscribe")),
                    frame -> observe(frame, options, runId, ledger, warmupCompleted, delivered)
            ).get(options.connectTimeout().toMillis(), TimeUnit.MILLISECONDS);

            for (int index = 0; index < options.producers(); index++) {
                TitanClient producer = newClient(options);
                producers.add(producer);
                producer.start();
                producer.connect().get(options.connectTimeout().toMillis(), TimeUnit.MILLISECONDS);
            }

            warmUp(options, producers, runId, warmupCompleted);

            for (int index = 0; index < producers.size(); index++) {
                TitanClient producer = producers.get(index);
                int producerId = index;
                executor.execute(() -> {
                    try {
                        startBarrier.await();
                    } catch (InterruptedException error) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    publish(producer, producerId, options, runId, ledger, sequence, deadlineNanos.get());
                });
            }

            long startedAt = System.nanoTime();
            long deadline = startedAt + options.completionTimeout().toNanos();
            deadlineNanos.set(deadline);
            startBarrier.countDown();

            executor.shutdown();
            boolean producersStopped = executor.awaitTermination(remaining(deadline), TimeUnit.NANOSECONDS);
            boolean deliveriesFinished = delivered.await(remaining(deadline), TimeUnit.NANOSECONDS);
            long elapsedNanos = System.nanoTime() - startedAt;
            boolean completedBeforeDeadline = producersStopped && deliveriesFinished;

            if (!producersStopped) {
                executor.shutdownNow();
                producersStopped = executor.awaitTermination(JOIN_GRACE_MILLIS, TimeUnit.MILLISECONDS);
            }

            MeasurementSnapshot snapshot = ledger.freeze();
            if (ledger.lateEvents() > 0) {
                System.err.println("Discarded " + ledger.lateEvents() + " events that arrived after the report was frozen");
            }

            return new PerfTestReport(
                    HexFormat.of().toHexDigits(runId),
                    options,
                    snapshot,
                    elapsedNanos,
                    completedBeforeDeadline,
                    producersStopped,
                    cleanupErrors
            );
        } finally {
            executor.shutdownNow();
            for (TitanClient producer : producers) {
                shutdown(producer, "producer", options, cleanupErrors);
            }
            shutdown(consumer, "consumer", options, cleanupErrors);
        }
    }

    /** Publishes until the run is out of identifiers or out of time. */
    private static void publish(
            TitanClient producer,
            int producerId,
            PerfTestOptions options,
            long runId,
            MeasurementLedger ledger,
            AtomicInteger sequence,
            long deadline
    ) {
        while (!Thread.currentThread().isInterrupted()) {
            if (remaining(deadline) == 0) {
                return;
            }
            int id = sequence.getAndIncrement();
            if (id >= options.messages()) {
                return;
            }

            long sentAt = System.nanoTime();
            CompletableFuture<StompFrames> result;
            try {
                result = producer.send(
                        options.group(),
                        options.destination(),
                        Buffer.heap().alloc(payload(options.payloadBytes(), runId, producerId, id, sentAt)),
                        sendHeaders(options, runId, producerId, id)
                );
            } catch (RuntimeException error) {
                // A synchronous exception alone does not prove that no bytes were submitted.
                classify(ledger, id, error);
                continue;
            }

            try {
                result.get(remaining(deadline), TimeUnit.NANOSECONDS);
                long latency = System.nanoTime() - sentAt;
                if (options.sendMode() == SendMode.RECEIPT) {
                    ledger.accepted(id, latency);
                } else {
                    ledger.writeSubmitted(id, latency);
                }
            } catch (TimeoutException error) {
                // The frame may be on the wire, in a buffer, or dropped. None of those is known.
                ledger.unknown(id);
            } catch (ExecutionException error) {
                classify(ledger, id, error.getCause());
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                ledger.unknown(id);
                return;
            }
        }
    }

    /**
     * Records a failed send.
     *
     * <p>Only the client's own refusal proves the bytes never left. A write that failed midway and
     * a connection that closed while a RECEIPT was outstanding are both unknown, and nothing here
     * turns them into a broker rejection the broker never sent.</p>
     */
    static void classify(MeasurementLedger ledger, int id, Throwable cause) {
        if (cause instanceof ClientException
                && "STOMP client is not connected".equals(cause.getMessage())) {
            ledger.localNotSent(id);
            return;
        }
        ledger.unknown(id);
    }

    /** Fills the queue and the consumer path before the measured phase starts. */
    private static void warmUp(
            PerfTestOptions options,
            List<TitanClient> producers,
            long runId,
            CountDownLatch warmupCompleted
    ) throws Exception {
        if (options.warmupMessages() == 0) {
            return;
        }

        long deadline = System.nanoTime() + options.completionTimeout().toNanos();
        for (int index = 0; index < options.warmupMessages(); index++) {
            int producerId = index % producers.size();
            // Warm-up messages carry their own phase and their own ordinal, so a warm-up message is
            // never mistaken for a measured one and duplicates stay visible within the warm-up too.
            int warmupSequence = warmupSequence(index);
            producers.get(producerId)
                    .send(
                            options.group(),
                            options.destination(),
                            Buffer.heap().alloc(
                                    payload(options.payloadBytes(), runId, producerId, warmupSequence, System.nanoTime())
                            ),
                            sendHeaders(options, runId, producerId, warmupSequence)
                    )
                    .get(remaining(deadline), TimeUnit.NANOSECONDS);
        }
        if (!warmupCompleted.await(remaining(deadline), TimeUnit.NANOSECONDS)) {
            throw new IllegalStateException("Warm-up messages did not complete before the timeout");
        }
    }

    /** Reads one delivered frame and credits it to the run, the warm-up, or nothing at all. */
    private static void observe(
            StompFrames frame,
            PerfTestOptions options,
            long runId,
            MeasurementLedger ledger,
            CountDownLatch warmupCompleted,
            CountDownLatch delivered
    ) {
        byte[] body = frame.body();
        if (body.length != options.payloadBytes()) {
            ledger.malformedMessage();
            return;
        }

        ByteBuffer payload = ByteBuffer.wrap(body);
        if (payload.getLong() != runId) {
            ledger.foreignMessage();
            return;
        }
        payload.getInt();
        int sequence = payload.getInt();
        long sentAt = payload.getLong();

        if (sequence < 0) {
            if (ledger.warmupReceived(warmupIndex(sequence)) == Reception.FIRST) {
                warmupCompleted.countDown();
            }
            return;
        }
        if (ledger.received(sequence, System.nanoTime() - sentAt) == Reception.FIRST) {
            delivered.countDown();
        }
    }

    private static Map<Elements, String> sendHeaders(PerfTestOptions options, long runId, int producerId, int sequence) {
        if (options.sendMode() != SendMode.RECEIPT) {
            return Map.of();
        }
        return Map.of(Elements.RECEIPT, receiptId(runId, producerId + "-" + phase(sequence)));
    }

    /**
     * Builds an identifier unique to this run, producer, and message.
     *
     * <p>The parts are joined with a dash rather than a colon. The broker escapes a colon inside a
     * header value on the way out and the inbound parser does not unescape it, so a receipt
     * identifier holding one never matches the request that is waiting for it.</p>
     */
    private static String receiptId(long runId, String suffix) {
        return HexFormat.of().toHexDigits(runId) + "-" + suffix;
    }

    /** Names the phase and ordinal a sequence number stands for. */
    private static String phase(int sequence) {
        return sequence < 0 ? "warmup" + warmupIndex(sequence) : "message" + sequence;
    }

    private static int warmupSequence(int index) {
        return -(index + 1);
    }

    private static int warmupIndex(int sequence) {
        return -sequence - 1;
    }

    private static long remaining(long deadline) {
        return Math.max(0, deadline - System.nanoTime());
    }

    private static byte[] payload(int size, long runId, int producerId, int sequence, long sentAtNanos) {
        byte[] payload = new byte[size];
        ByteBuffer.wrap(payload)
                .putLong(runId)
                .putInt(producerId)
                .putInt(sequence)
                .putLong(sentAtNanos);
        return payload;
    }

    private static void shutdown(TitanClient client, String role, PerfTestOptions options, List<String> cleanupErrors) {
        try {
            client.shutdown(options.connectTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (RuntimeException error) {
            cleanupErrors.add("Failed to shut down the " + role + " connection: " + error.getMessage());
        }
    }

    private static TitanClient newClient(PerfTestOptions options) {
        TitanClient.Builder builder = TitanClient.builder()
                .host(options.host())
                .port(options.port())
                .worker(1)
                .connectTimeout(options.connectTimeout())
                .session(StompSessionOption.builder().heartbeatX(0L).heartbeatY(0L).build());
        if (options.transport() == Transport.WEBSOCKET) {
            builder.webSocket(options.webSocketPath());
        }
        return builder.build();
    }
}
