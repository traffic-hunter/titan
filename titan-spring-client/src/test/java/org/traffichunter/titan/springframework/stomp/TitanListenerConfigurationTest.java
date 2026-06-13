package org.traffichunter.titan.springframework.stomp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.messaging.Message;
import org.springframework.messaging.converter.SmartMessageConverter;
import org.springframework.messaging.handler.invocation.HandlerMethodArgumentResolverComposite;
import org.springframework.messaging.support.MessageBuilder;
import org.traffichunter.titan.core.codec.stomp.StompCommand;
import org.traffichunter.titan.core.codec.stomp.StompFrame;
import org.traffichunter.titan.core.codec.stomp.StompFrames;
import org.traffichunter.titan.core.codec.stomp.StompHeaders;
import org.traffichunter.titan.springframework.stomp.annotation.TitanListener;
import org.traffichunter.titan.springframework.stomp.listener.TitanListenerConfiguration;
import org.traffichunter.titan.springframework.stomp.messaging.TitanSpringMessageAdapter;

class TitanListenerConfigurationTest {

    @Test
    void listener_uses_titan_client_manager_by_default() throws NoSuchMethodException {
        Object defaultClientRef = TitanListener.class
                .getDeclaredMethod("clientRef")
                .getDefaultValue();

        assertEquals("titanClientManager", defaultClientRef);
    }

    @Test
    void resolver_composite_resolves_stomp_frame_and_payload() throws Exception {
        TitanListenerConfiguration configuration = new TitanListenerConfiguration();
        SmartMessageConverter converter = configuration.titanMessageConverter();
        HandlerMethodArgumentResolverComposite composite = configuration.titanResolverComposite(converter);

        StompFrame frame = StompFrame.create(
                StompHeaders.create(),
                StompCommand.MESSAGE,
                "hello".getBytes(StandardCharsets.UTF_8)
        );
        Message<byte[]> stompMessage = TitanSpringMessageAdapter.from(frame);

        Object resolvedFrame = composite.resolveArgument(parameter("onFrame", StompFrames.class), stompMessage);
        Object resolvedPayload = composite.resolveArgument(parameter("onPayload", String.class), stompMessage);

        assertSame(frame, resolvedFrame);
        assertEquals("hello", resolvedPayload);
    }

    @Test
    void resolver_composite_resolves_message_parameter() throws Exception {
        TitanListenerConfiguration configuration = new TitanListenerConfiguration();
        HandlerMethodArgumentResolverComposite composite =
                configuration.titanResolverComposite(configuration.titanMessageConverter());
        Message<byte[]> message = MessageBuilder.withPayload("hello".getBytes(StandardCharsets.UTF_8)).build();

        Object resolvedMessage = composite.resolveArgument(parameter("onMessage", Message.class), message);

        assertSame(message, resolvedMessage);
    }

    private static MethodParameter parameter(String name, Class<?> type) throws NoSuchMethodException {
        Method method = FixtureHandler.class.getDeclaredMethod(name, type);
        return new MethodParameter(method, 0);
    }

    static final class FixtureHandler {
        void onFrame(StompFrames frame) {}
        void onPayload(String payload) {}
        void onMessage(Message<byte[]> message) {}
    }
}
