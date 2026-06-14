package org.traffichunter.titan.core.transport.stomp.option;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StompClientOptionTest {

    @Test
    void uses_short_connect_timeout_by_default() {
        StompClientOption option = StompClientOption.builder().build();

        assertThat(option.connectTimeout()).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    void accepts_custom_connect_timeout() {
        StompClientOption option = StompClientOption.builder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();

        assertThat(option.connectTimeout()).isEqualTo(Duration.ofSeconds(2));
    }

    @Test
    void rejects_non_positive_connect_timeout() {
        assertThatThrownBy(() -> StompClientOption.builder()
                .connectTimeout(Duration.ZERO)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("connectTimeout must be positive");
    }
}
