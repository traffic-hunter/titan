package org.traffichunter.titan.benchmark;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.traffichunter.titan.core.message.Message;
import org.traffichunter.titan.core.message.Priority;
import org.traffichunter.titan.core.util.Destination;
import org.traffichunter.titan.core.util.buffer.Buffer;
import org.traffichunter.titan.fanout.CompletableResult;
import org.traffichunter.titan.fanout.FanoutGateway;
import org.traffichunter.titan.fanout.FanoutMode;
import org.traffichunter.titan.fanout.exporter.FanoutExporter;

@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class FanoutGatewayBenchmark {

    @Param({"platform", "virtual"})
    public String mode;

    @Param({"64", "256"})
    public int batchSize;

    private FanoutGateway gateway;
    private Destination destination;
    private Message message;
    private List<Message> batchMessages;

    @Setup(Level.Trial)
    public void setUp() throws Exception {
        LongAdder exportCount = new LongAdder();
        destination = Destination.create("/benchmark/fanout");
        message = Message.builder()
                .priority(Priority.DEFAULT)
                .destination(destination)
                .createdAt(Instant.now())
                .producerId("benchmark-producer")
                .body(Buffer.alloc("payload"))
                .build();
        batchMessages = createBatchMessages(destination, batchSize);

        gateway = FanoutMode.resolveMode(mode).fanoutGateway(new CountingNoopExporter(exportCount));
        gateway.fanout(destination);
        gateway.publish(message).get(1, TimeUnit.SECONDS);
    }

    @TearDown(Level.Trial)
    public void tearDown() throws Exception {
        gateway.close();
    }

    @Benchmark
    @Threads(1)
    public void publishSingleThreadOneMessage() throws Exception {
        gateway.publish(message).get(1, TimeUnit.SECONDS);
    }

    @Benchmark
    @Threads(16)
    public void publishConcurrentOneMessage() throws Exception {
        gateway.publish(message).get(1, TimeUnit.SECONDS);
    }

    @Benchmark
    @Threads(16)
    public void publishConcurrentBatch() throws Exception {
        List<Future<Void>> futures = new ArrayList<>(batchMessages.size());
        for (Message batchMessage : batchMessages) {
            futures.add(gateway.publish(batchMessage));
        }
        for (Future<Void> future : futures) {
            future.get(1, TimeUnit.SECONDS);
        }
    }

    private static List<Message> createBatchMessages(Destination destination, int batchSize) {
        List<Message> messages = new ArrayList<>(batchSize);
        for (int i = 0; i < batchSize; i++) {
            messages.add(Message.builder()
                    .priority(Priority.DEFAULT)
                    .destination(destination)
                    .createdAt(Instant.now())
                    .producerId("benchmark-producer-" + i)
                    .body(Buffer.alloc("payload-" + i))
                    .build());
        }
        return messages;
    }

    private static final class CountingNoopExporter implements FanoutExporter {
        private final LongAdder count;

        private CountingNoopExporter(LongAdder count) {
            this.count = count;
        }

        @Override
        public String name() {
            return "noop";
        }

        @Override
        public CompletableResult export(Destination destination, Buffer payload) {
            count.increment();
            return null;
        }

        @Override
        public CompletableResult export(Destination destination, Message payload) {
            count.increment();
            return null;
        }
    }
}
