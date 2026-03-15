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
import org.traffichunter.titan.core.dispatcher.Dispatcher;
import org.traffichunter.titan.core.dispatcher.DispatcherQueue;
import org.traffichunter.titan.core.dispatcher.MapDispatcher;
import org.traffichunter.titan.core.dispatcher.TrieDispatcher;
import org.traffichunter.titan.core.message.Message;
import org.traffichunter.titan.core.message.Priority;
import org.traffichunter.titan.core.util.RoutingKey;

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class DispatcherBenchmark {

    private Dispatcher trieDispatcher;
    private Dispatcher mapDispatcher;
    private RoutingKey exactKey;
    private RoutingKey wildcardKey;
    private DispatcherQueue trieExactQueue;
    private DispatcherQueue mapExactQueue;
    private Message message;

    @Setup(Level.Trial)
    public void setUp() {
        trieDispatcher = new TrieDispatcher();
        mapDispatcher = new MapDispatcher(10_240);
        exactKey = RoutingKey.create("/topic/bench/5000");
        wildcardKey = RoutingKey.create("/topic/*");
        trieExactQueue = DispatcherQueue.create(exactKey);
        mapExactQueue = DispatcherQueue.create(exactKey);

        List<RoutingKey> keys = new ArrayList<>(10_000);
        for (int i = 0; i < 10_000; i++) {
            keys.add(RoutingKey.create("/topic/bench/" + i));
        }

        for (RoutingKey key : keys) {
            trieDispatcher.insert(key, key.equals(exactKey) ? trieExactQueue : DispatcherQueue.create(key));
            mapDispatcher.insert(key, key.equals(exactKey) ? mapExactQueue : DispatcherQueue.create(key));
        }

        message = Message.builder()
                .priority(Priority.DEFAULT)
                .routingKey(exactKey)
                .createdAt(Instant.now())
                .isRecovery(false)
                .producerId("benchmark-producer")
                .body("payload".getBytes())
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
        return trieDispatcher.find(exactKey);
    }

    @Benchmark
    public DispatcherQueue mapFindExactQueue() {
        return mapDispatcher.find(exactKey);
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
    public int trieDispatchWildcard() {
        return trieDispatcher.dispatch(wildcardKey).size();
    }

    @Benchmark
    public int mapDispatchWildcard() {
        return mapDispatcher.dispatch(wildcardKey).size();
    }
}
