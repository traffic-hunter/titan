package org.traffichunter.titan.benchmark;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
import org.openjdk.jmh.annotations.Warmup;
import org.traffichunter.titan.dispatch.DispatcherQueue;
import org.traffichunter.titan.dispatch.MapPathFinder;
import org.traffichunter.titan.dispatch.TriePathFinder;
import org.traffichunter.titan.core.message.Message;
import org.traffichunter.titan.core.util.Destination;

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class DispatcherBenchmark {

    private TriePathFinder triePathFinder;
    private MapPathFinder mapPathFinder;
    private Destination exactKey;
    private DispatcherQueue trieExactQueue;
    private DispatcherQueue mapExactQueue;
    private Message message;

    @Setup(Level.Trial)
    public void setUp() {
        triePathFinder = new TriePathFinder();
        mapPathFinder = new MapPathFinder(10_240);
        exactKey = Destination.create("/topic/bench/5000");

        List<Destination> keys = new ArrayList<>(10_000);
        for (int i = 0; i < 10_000; i++) {
            keys.add(Destination.create("/topic/bench/" + i));
        }

        for (Destination key : keys) {
            triePathFinder.getOrPut(key);
            mapPathFinder.getOrPut(key);
        }

        trieExactQueue = triePathFinder.get(exactKey);
        mapExactQueue = mapPathFinder.get(exactKey);

        message = Message.builder()
                .destination(exactKey)
                .createdAt(Instant.now())
                .producerId("benchmark-producer")
                .body("payload".getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .build();
    }

    @Setup(Level.Invocation)
    public void prepareQueues() {
        trieExactQueue.clear();
        mapExactQueue.clear();
        trieExactQueue.enqueue(message);
        mapExactQueue.enqueue(message);
    }

    @Benchmark
    public DispatcherQueue trieFindExactQueue() {
        return triePathFinder.get(exactKey);
    }

    @Benchmark
    public DispatcherQueue mapFindExactQueue() {
        return mapPathFinder.get(exactKey);
    }

    @Benchmark
    public DispatcherQueue trieGetOrPutExactQueueHit() {
        return triePathFinder.getOrPut(exactKey);
    }

    @Benchmark
    public DispatcherQueue mapGetOrPutExactQueueHit() {
        DispatcherQueue queue = mapPathFinder.getOrPut(exactKey);
        return queue == null ? mapPathFinder.get(exactKey) : queue;
    }

    @Benchmark
    public boolean trieExistsExactKey() {
        return triePathFinder.exists(exactKey);
    }

    @Benchmark
    public boolean mapExistsExactKey() {
        return mapPathFinder.exists(exactKey);
    }

    @Benchmark
    public Message trieDispatchExactQueue() throws InterruptedException {
        trieExactQueue.enqueue(message);
        Message dispatched = trieExactQueue.dispatch(1, TimeUnit.MILLISECONDS);
        if (dispatched != null) {
            trieExactQueue.complete(dispatched);
        }
        return dispatched;
    }

    @Benchmark
    public Message mapDispatchExactQueue() throws InterruptedException {
        mapExactQueue.enqueue(message);
        Message dispatched = mapExactQueue.dispatch(1, TimeUnit.MILLISECONDS);
        if (dispatched != null) {
            mapExactQueue.complete(dispatched);
        }
        return dispatched;
    }
}
