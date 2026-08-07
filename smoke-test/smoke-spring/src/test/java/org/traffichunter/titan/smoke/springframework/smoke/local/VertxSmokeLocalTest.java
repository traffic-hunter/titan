package org.traffichunter.titan.smoke.springframework.smoke.local;

import org.springframework.context.annotation.Import;
import org.traffichunter.titan.smoke.springframework.smoke.junit.VertxSmokeTest;

@VertxSmokeTest
@Import(SmokeConfiguration.class)
class VertxSmokeLocalTest extends AbstractTitanSmokeLocalTest {

    @Override
    protected String clientName() {
        return "vertx";
    }
}
