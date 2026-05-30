package org.traffichunter.titan.core.test.implementation;

import org.junit.jupiter.api.Test;
import org.traffichunter.titan.core.util.IdGenerator;

import static org.assertj.core.api.Assertions.*;

/**
 * @author yun
 */
class IdGeneratorTest {

    @Test
    void generate_random_id_test() {
        String randomId = IdGenerator.randomId(null);

        assertThat(randomId).isNotNull();
        assertThat(randomId).startsWith("titan-");
        assertThat(randomId).hasSize("titan-".length() + 22);
    }

    @Test
    void generate_random_id_prefix_test() {
        String randomId = IdGenerator.randomId("qwer");

        assertThat(randomId).isNotNull();
        assertThat(randomId).startsWith("qwer-");
        assertThat(randomId).hasSize("qwer-".length() + 22);
    }
}
