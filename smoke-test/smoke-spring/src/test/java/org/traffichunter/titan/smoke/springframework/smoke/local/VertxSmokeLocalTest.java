package org.traffichunter.titan.smoke.springframework.smoke.local;

import org.springframework.context.annotation.Import;
import org.traffichunter.titan.core.transport.stomp.VertxStompClient;
import org.traffichunter.titan.core.transport.stomp.client.StompClient;
import org.traffichunter.titan.core.transport.stomp.client.StompClientOperations;
import org.traffichunter.titan.core.transport.stomp.client.VertxStompClientOperations;
import org.traffichunter.titan.smoke.springframework.smoke.junit.VertxSmokeTest;

@VertxSmokeTest
@Import(SmokeConfiguration.class)
class VertxSmokeLocalTest extends AbstractTitanSmokeLocalTest {

    @Override
    protected Class<? extends StompClient> clientType() {
        return VertxStompClient.class;
    }

    @Override
    protected Class<? extends StompClientOperations> operationsType() {
        return VertxStompClientOperations.class;
    }
}
