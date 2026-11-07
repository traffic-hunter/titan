package org.traffichunter.titan.core.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * @author yun
 */
class OptionKeyTest {

    @Test
    void key_is_known_only_after_register() {
        OptionKey<Integer> key = OptionKey.intKey("option-key-test.registered");
        assertThat(OptionKey.names()).doesNotContain("option-key-test.registered");

        OptionKey.register(List.of(key));
        OptionKey.register(List.of(OptionKey.intKey("option-key-test.registered")));

        assertThat(OptionKey.names()).contains("option-key-test.registered");
    }

    @Test
    void reject_registering_same_name_with_another_type() {
        OptionKey.register(List.of(OptionKey.intKey("option-key-test.typed")));

        assertThatThrownBy(() -> OptionKey.register(List.of(OptionKey.booleanKey("option-key-test.typed"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Option option-key-test.typed is already registered as Integer");
    }

    @Test
    void reject_boolean_other_than_true_or_false() {
        OptionKey<Boolean> key = OptionKey.booleanKey("option-key-test.flag");

        assertThat(key.get(Map.of("option-key-test.flag", "TRUE"))).isTrue();
        assertThatThrownBy(() -> key.get(Map.of("option-key-test.flag", "ture")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid value for option-key-test.flag: ture");
    }

    @Test
    void invalid_number_names_the_key() {
        OptionKey<Integer> key = OptionKey.intKey("option-key-test.size");

        assertThatThrownBy(() -> key.get(Map.of("option-key-test.size", "1MB")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid value for option-key-test.size: 1MB");
    }

    @Test
    void trim_value_and_treat_blank_as_unset() {
        OptionKey<Long> key = OptionKey.longKey("option-key-test.millis");

        assertThat(key.get(Map.of("option-key-test.millis", " 1024 "))).isEqualTo(1024L);
        assertThat(key.get(Map.of("option-key-test.millis", " "))).isNull();
        assertThat(key.get(Map.of(), 7L)).isEqualTo(7L);
    }
}
