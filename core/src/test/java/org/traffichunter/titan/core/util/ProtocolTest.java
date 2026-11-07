package org.traffichunter.titan.core.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * @author yun
 */
class ProtocolTest {

    @ParameterizedTest
    @ValueSource(strings = {"v12.stomp", "stomp", "V12.STOMP"})
    void stomp_subprotocol_resolves_to_stomp(String subProtocol) {
        assertThat(Protocol.subProtocol(subProtocol)).isEqualTo(Protocol.STOMP);
    }

    @ParameterizedTest
    @ValueSource(strings = {"v50.mqtt", "mqtt"})
    void mqtt_subprotocol_is_unknown(String subProtocol) {
        assertThatThrownBy(() -> Protocol.subProtocol(subProtocol))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(subProtocol);
    }
}
