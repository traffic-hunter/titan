package org.traffichunter.titan.core.test.implementation;

import org.junit.jupiter.api.Test;
import org.traffichunter.titan.core.util.IdGenerator;

import java.util.Base64;

import static org.assertj.core.api.Assertions.*;

/**
 * @author yun
 */
class IdGeneratorTest {

    @Test
    void generate_random_id_test() {
        String randomId = IdGenerator.randomId16("");

        assertThat(randomId).isNotNull();
        assertThat(randomId).startsWith("titan-");
        assertThat(randomId).hasSize("titan-".length() + 16);
        assertThat(randomId.substring("titan-".length())).matches("[A-Za-z0-9]{16}");
    }

    @Test
    void generate_random_id_prefix_test() {
        String randomId = IdGenerator.randomId16("qwer");

        assertThat(randomId).isNotNull();
        assertThat(randomId).startsWith("qwer-");
        assertThat(randomId).hasSize("qwer-".length() + 16);
        assertThat(randomId.substring("qwer-".length())).matches("[A-Za-z0-9]{16}");
    }

    @Test
    void generate_random_id_without_prefix_test() {
        String randomId = IdGenerator.randomId16(null);

        assertThat(randomId).matches("[A-Za-z0-9]{16}");
    }

    @Test
    void generate_sixteen_byte_base64_id_test() {
        String randomId = IdGenerator.randomBase64Id16();

        assertThat(Base64.getDecoder().decode(randomId)).hasSize(16);
    }
}
