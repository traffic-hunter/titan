package org.traffichunter.titan.core.test.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.traffichunter.titan.core.channel.Channel;
import org.traffichunter.titan.core.channel.EventLoopGroups;
import org.traffichunter.titan.core.channel.NetChannel;
import org.traffichunter.titan.core.transport.InetClient;
import org.traffichunter.titan.core.util.concurrent.Promise;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author yun
 */
@Timeout(10)
class InetClientFailureIntegrationTest {

    @Test
    void remove_created_channel_when_initializer_fails() {
        EventLoopGroups groups = EventLoopGroups.group(1);
        InetClient client = InetClient.open(groups);
        IllegalStateException failure = new IllegalStateException("initializer failed");
        AtomicReference<Channel> created = new AtomicReference<>();
        client.onChannel(channel -> {
            created.set(channel);
            throw failure;
        });
        client.start();
        try {
            Promise<NetChannel> result = client.connect("localhost", 1, 1, TimeUnit.SECONDS);

            assertThat(result.isFailed()).isTrue();
            assertThat(result.error()).isSameAs(failure);
            assertThat(client.channels()).isEmpty();
            assertThat(created.get()).isNotNull().satisfies(channel -> {
                assertThat(channel.isClosed()).isTrue();
                assertThat(channel.isOpen()).isFalse();
            });
        } finally {
            client.shutdown(1, TimeUnit.SECONDS);
        }
    }

}
