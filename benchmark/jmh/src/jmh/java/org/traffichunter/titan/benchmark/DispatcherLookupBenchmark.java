package org.traffichunter.titan.benchmark;

import java.nio.charset.StandardCharsets;
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
import org.traffichunter.titan.core.util.Destination;
import org.traffichunter.titan.dispatch.DispatcherQueue;
import org.traffichunter.titan.dispatch.MapPathFinder;
import org.traffichunter.titan.dispatch.TriePathFinder;

/**
 * Trie and map path finders shared by every benchmark thread. Both hold 10,000 queues,
 * 100 under each of 100 parents. Run with {@code -t 16} for the multi-threaded case.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class DispatcherLookupBenchmark {

    private static final int PARENTS = 100;
    private static final int QUEUES_PER_PARENT = 100;
    private static final int KEYS = 64;

    private TriePathFinder triePathFinder;
    private MapPathFinder mapPathFinder;
    private Destination[] keys;
    private byte[][] wirePaths;
    private Destination wildcard;

    @State(Scope.Thread)
    public static class Cursor {

        private int next;

        int next() {
            return next++ & (KEYS - 1);
        }
    }

    @Setup(Level.Trial)
    public void setUp() {
        triePathFinder = new TriePathFinder();
        mapPathFinder = new MapPathFinder(PARENTS * QUEUES_PER_PARENT);
        for (int parent = 0; parent < PARENTS; parent++) {
            for (int queue = 0; queue < QUEUES_PER_PARENT; queue++) {
                Destination destination = Destination.create("/topic/p" + parent + "/" + queue);
                triePathFinder.getOrPut(destination);
                mapPathFinder.getOrPut(destination);
            }
        }

        keys = new Destination[KEYS];
        wirePaths = new byte[KEYS][];
        for (int i = 0; i < KEYS; i++) {
            keys[i] = Destination.create("/topic/p" + i + "/" + i);
            wirePaths[i] = keys[i].path().getBytes(StandardCharsets.UTF_8);
        }
        wildcard = Destination.create("/topic/p50/*");
    }

    @Benchmark
    public DispatcherQueue trieGetOrPut(Cursor cursor) {
        return triePathFinder.getOrPut(keys[cursor.next()]);
    }

    @Benchmark
    public DispatcherQueue mapGetOrPut(Cursor cursor) {
        return mapPathFinder.getOrPut(keys[cursor.next()]);
    }

    @Benchmark
    public DispatcherQueue trieGetOrPutNewKey(Cursor cursor) {
        return triePathFinder.getOrPut(newKey(cursor));
    }

    @Benchmark
    public DispatcherQueue mapGetOrPutNewKey(Cursor cursor) {
        return mapPathFinder.getOrPut(newKey(cursor));
    }

    /** Cost of building the key alone, to subtract from the two benchmarks above. */
    @Benchmark
    public Destination newKeyOnly(Cursor cursor) {
        return newKey(cursor);
    }

    @Benchmark
    public List<DispatcherQueue> trieSearchWildcard() {
        return triePathFinder.searchAll(wildcard);
    }

    @Benchmark
    public List<DispatcherQueue> mapSearchWildcard() {
        return mapPathFinder.searchAll(wildcard);
    }

    // A decoded frame hands over a new string each time, so its hash is not cached yet.
    private Destination newKey(Cursor cursor) {
        return Destination.create(new String(wirePaths[cursor.next()], StandardCharsets.UTF_8));
    }
}
