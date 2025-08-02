package org.traffichunter.titan.servicediscovery.key;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.traffichunter.titan.servicediscovery.RoutingKey;

/**
 * @author yungwang-o
 */
class RoutingKeyTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "user.key",
            "user.*",
            "user.key.*",
            "order.created.*"
    })
    void check_valid_routing_key(String key) {
        Assertions.assertDoesNotThrow(() -> RoutingKey.create(key));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "#.user",
            "*.user.test",
            "user.*.test",
            "*.*.*"
    })
    void check_invalid_routing_key(String key) {
        Assertions.assertThrows(IllegalArgumentException.class, () -> RoutingKey.create(key));
    }
}