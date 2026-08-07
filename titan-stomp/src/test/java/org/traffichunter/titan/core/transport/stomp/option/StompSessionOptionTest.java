package org.traffichunter.titan.core.transport.stomp.option;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StompSessionOptionTest {

    @Test
    void uses_session_defaults() {
        StompSessionOption option = StompSessionOption.builder().build();

        assertThat(option.heartbeatX()).isEqualTo(1000L);
        assertThat(option.heartbeatY()).isEqualTo(1000L);
        assertThat(option.maxFrameLength()).isEqualTo(65536);
    }

    @Test
    void accepts_custom_heartbeat() {
        StompSessionOption option = StompSessionOption.builder()
                .heartbeatX(2000L)
                .heartbeatY(3000L)
                .build();

        assertThat(option.heartbeatX()).isEqualTo(2000L);
        assertThat(option.heartbeatY()).isEqualTo(3000L);
    }

    @Test
    void rejects_negative_heartbeat() {
        assertThatThrownBy(() -> StompSessionOption.builder()
                .heartbeatX(-1L)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("heartbeat values must be >= 0");
    }
}
