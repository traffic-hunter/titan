package org.traffichunter.titan.smoke.springframework.smoke.local;

import org.springframework.context.annotation.Import;
import org.traffichunter.titan.core.transport.stomp.VertxStompClient;
import org.traffichunter.titan.core.transport.stomp.client.StompClient;
import org.traffichunter.titan.core.transport.stomp.client.StompOperations;
import org.traffichunter.titan.core.transport.stomp.client.VertxStompOperations;
import org.traffichunter.titan.smoke.springframework.smoke.junit.VertxSmokeTest;

@VertxSmokeTest
@Import(SmokeConfiguration.class)
class VertxSmokeLocalTest extends AbstractTitanSmokeLocalTest {

    @Override
    protected Class<? extends StompClient> clientType() {
        return VertxStompClient.class;
    }

    @Override
    protected Class<? extends StompOperations> operationsType() {
        return VertxStompOperations.class;
    }
}
