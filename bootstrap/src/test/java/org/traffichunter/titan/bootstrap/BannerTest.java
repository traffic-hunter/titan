package org.traffichunter.titan.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BannerTest {

    @Test
    void exposes_only_titan_specific_banner_resource() {
        ClassLoader classLoader = Banner.class.getClassLoader();

        assertThat(classLoader.getResource("titan-banner.txt")).isNotNull();
        assertThat(classLoader.getResource("banner.txt")).isNull();
    }
}
