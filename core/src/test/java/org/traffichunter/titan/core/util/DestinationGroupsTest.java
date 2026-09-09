package org.traffichunter.titan.core.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * @author yun
 */
class DestinationGroupsTest {

    @Test
    void null_normalizes_to_default() {
        assertThat(DestinationGroups.normalize(null)).isEqualTo(DestinationGroups.DEFAULT);
    }

    @Test
    void blank_normalizes_to_default() {
        assertThat(DestinationGroups.normalize("   ")).isEqualTo(DestinationGroups.DEFAULT);
    }

    @Test
    void valid_name_is_kept() {
        assertThat(DestinationGroups.normalize("market-1_A")).isEqualTo("market-1_A");
    }

    @Test
    void slash_is_rejected() {
        assertThatThrownBy(() -> DestinationGroups.normalize("bad/name"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bad/name");
    }

    @Test
    void over_64_chars_is_rejected() {
        String name = "a".repeat(65);

        assertThat(DestinationGroups.isValid("a".repeat(64))).isTrue();
        assertThat(DestinationGroups.isValid(name)).isFalse();
    }
}
