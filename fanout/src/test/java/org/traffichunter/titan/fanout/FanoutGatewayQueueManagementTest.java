package org.traffichunter.titan.fanout;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.traffichunter.titan.core.message.Message;
import org.traffichunter.titan.core.message.dispatcher.DispatcherQueue;
import org.traffichunter.titan.core.message.dispatcher.DispatcherQueueDeleteResult;
import org.traffichunter.titan.core.message.dispatcher.TrieDispatcher;
import org.traffichunter.titan.core.util.Destination;
import org.traffichunter.titan.core.util.buffer.Buffer;
import org.traffichunter.titan.fanout.exporter.FanoutExporter;

class FanoutGatewayQueueManagementTest {

    @Test
    void delete_queue_rejects_non_empty_queue_without_force() {
        TrieDispatcher dispatcher = new TrieDispatcher();
        ThreadPoolExecutorFanoutGateway gateway = new ThreadPoolExecutorFanoutGateway(
                noopExporter(),
                dispatcher
        );
        Destination destination = Destination.create("/queue/delete-safe");
        gateway.createQueue(destination, 10).enqueue(message(destination));

        DispatcherQueueDeleteResult result = gateway.deleteQueue(destination, false);

        assertThat(result.status()).isEqualTo(DispatcherQueueDeleteResult.Status.NOT_EMPTY);
        assertThat(dispatcher.get(destination)).isNotNull();

        gateway.close();
    }

    @Test
    void delete_queue_with_force_removes_queue_and_allows_auto_create_later() throws Exception {
        TrieDispatcher dispatcher = new TrieDispatcher();
        ThreadPoolExecutorFanoutGateway gateway = new ThreadPoolExecutorFanoutGateway(
                noopExporter(),
                dispatcher
        );
        Destination destination = Destination.create("/queue/delete-force");
        DispatcherQueue first = gateway.createQueue(destination, 10);
        first.enqueue(message(destination));

        DispatcherQueueDeleteResult result = gateway.deleteQueue(destination, true);

        assertThat(result.isDeleted()).isTrue();
        assertThat(dispatcher.get(destination)).isNull();

        gateway.publish(message(destination)).get();

        assertThat(dispatcher.get(destination)).isNotNull();
        assertThat(dispatcher.get(destination)).isNotSameAs(first);

        gateway.close();
    }

    private static FanoutExporter noopExporter() {
        return new FanoutExporter() {
            @Override
            public String name() {
                return "noop";
            }

            @Override
            public AggregationResult export(Destination destination, Buffer payload) {
                return AggregationResult.completed(List.of(destination), 0, 0, 0);
            }
        };
    }

    private static Message message(Destination destination) {
        return Message.builder()
                .destination(destination)
                .createdAt(Instant.now())
                .producerId("test")
                .body(Buffer.alloc("test".getBytes()))
                .build();
    }
}
