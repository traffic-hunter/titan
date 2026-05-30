package org.traffichunter.titan.smoke.springframework.smoke.local;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
class SmokeConfiguration {

    @Bean
    SmokeListener smokeListener() {
        return new SmokeListener();
    }
}
