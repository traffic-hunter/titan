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
import org.traffichunter.titan.core.message.dispatcher.DispatcherQueue;
import org.traffichunter.titan.core.message.dispatcher.MapDispatcher;
import org.traffichunter.titan.core.message.dispatcher.TrieDispatcher;
import org.traffichunter.titan.core.message.Message;
import org.traffichunter.titan.core.message.Priority;
import org.traffichunter.titan.core.util.Destination;
import org.traffichunter.titan.core.util.buffer.Buffer;

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class DispatcherBenchmark {

    private TrieDispatcher trieDispatcher;
    private MapDispatcher mapDispatcher;
    private Destination exactKey;
    private DispatcherQueue trieExactQueue;
    private DispatcherQueue mapExactQueue;
    private Message message;

    @Setup(Level.Trial)
    public void setUp() {
        trieDispatcher = new TrieDispatcher();
        mapDispatcher = new MapDispatcher(10_240);
        exactKey = Destination.create("/topic/bench/5000");

        List<Destination> keys = new ArrayList<>(10_000);
        for (int i = 0; i < 10_000; i++) {
            keys.add(Destination.create("/topic/bench/" + i));
        }

        for (Destination key : keys) {
            trieDispatcher.getOrPut(key);
            mapDispatcher.getOrPut(key);
        }

        trieExactQueue = trieDispatcher.get(exactKey);
        mapExactQueue = mapDispatcher.get(exactKey);

        message = Message.builder()
                .priority(Priority.DEFAULT)
                .destination(exactKey)
                .createdAt(Instant.now())
                .producerId("benchmark-producer")
                .body(Buffer.alloc("payload"))
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
        return trieDispatcher.get(exactKey);
    }

    @Benchmark
    public DispatcherQueue mapFindExactQueue() {
        return mapDispatcher.get(exactKey);
    }

    @Benchmark
    public DispatcherQueue trieGetOrPutExactQueueHit() {
        return trieDispatcher.getOrPut(exactKey);
    }

    @Benchmark
    public DispatcherQueue mapGetOrPutExactQueueHit() {
        DispatcherQueue queue = mapDispatcher.getOrPut(exactKey);
        return queue == null ? mapDispatcher.get(exactKey) : queue;
    }

    @Benchmark
    public boolean trieExistsExactKey() {
        return trieDispatcher.exists(exactKey);
    }

    @Benchmark
    public boolean mapExistsExactKey() {
        return mapDispatcher.exists(exactKey);
    }

    @Benchmark
    public Message trieDispatchExactQueue() throws InterruptedException {
        trieExactQueue.enqueue(message);
        return trieExactQueue.dispatch(1, TimeUnit.MILLISECONDS);
    }

    @Benchmark
    public Message mapDispatchExactQueue() throws InterruptedException {
        mapExactQueue.enqueue(message);
        return mapExactQueue.dispatch(1, TimeUnit.MILLISECONDS);
    }
}
