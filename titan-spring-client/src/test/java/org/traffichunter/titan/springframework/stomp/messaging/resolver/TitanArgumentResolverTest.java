package org.traffichunter.titan.springframework.stomp.messaging.resolver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.messaging.Message;
import org.springframework.messaging.converter.ByteArrayMessageConverter;
import org.springframework.messaging.converter.CompositeMessageConverter;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.converter.SmartMessageConverter;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.support.MessageBuilder;
import org.traffichunter.titan.core.codec.stomp.StompCommand;
import org.traffichunter.titan.core.codec.stomp.StompFrame;
import org.traffichunter.titan.core.codec.stomp.StompHeaders;
import org.traffichunter.titan.springframework.stomp.messaging.TitanSpringMessageAdapter;
import org.traffichunter.titan.springframework.stomp.messaging.converter.StompFrameMessageConverter;

class TitanArgumentResolverTest {

    private static final SmartMessageConverter CONVERTER = new CompositeMessageConverter(List.of(
            new StompFrameMessageConverter(),
            new ByteArrayMessageConverter(),
            new StringMessageConverter(),
            new MappingJackson2MessageConverter()
    ));

    @Test
    void stomp_resolver_supports_message_and_stomp_frame() throws Exception {
        TitanStompHandlerMethodArgumentResolver resolver = new TitanStompHandlerMethodArgumentResolver(CONVERTER);

        assertTrue(resolver.supportsParameter(parameter("onMessage", Message.class)));
        assertTrue(resolver.supportsParameter(parameter("onFrame", StompFrame.class)));
        assertFalse(resolver.supportsParameter(parameter("onPayload", String.class)));
    }

    @Test
    void stomp_resolver_returns_message_for_message_parameter() throws Exception {
        TitanStompHandlerMethodArgumentResolver resolver = new TitanStompHandlerMethodArgumentResolver(CONVERTER);
        Message<byte[]> message = MessageBuilder.withPayload("hello".getBytes(StandardCharsets.UTF_8)).build();

        Object resolved = resolver.resolveArgument(parameter("onMessage", Message.class), message);

        assertSame(message, resolved);
    }

    @Test
    void stomp_resolver_converts_to_stomp_frame_parameter() throws Exception {
        TitanStompHandlerMethodArgumentResolver resolver = new TitanStompHandlerMethodArgumentResolver(CONVERTER);
        StompFrame frame = createFrame("hello");
        Message<byte[]> message = TitanSpringMessageAdapter.from(frame);

        Object resolved = resolver.resolveArgument(parameter("onFrame", StompFrame.class), message);

        assertSame(frame, resolved);
    }

    @Test
    void payload_resolver_supports_non_message_non_stomp_types_only() throws Exception {
        TitanPayloadHandlerMethodArgumentResolver resolver = new TitanPayloadHandlerMethodArgumentResolver(CONVERTER);

        assertTrue(resolver.supportsParameter(parameter("onPayload", String.class)));
        assertFalse(resolver.supportsParameter(parameter("onMessage", Message.class)));
        assertFalse(resolver.supportsParameter(parameter("onFrame", StompFrame.class)));
    }

    @Test
    void payload_resolver_converts_byte_payload_to_string() throws Exception {
        TitanPayloadHandlerMethodArgumentResolver resolver = new TitanPayloadHandlerMethodArgumentResolver(CONVERTER);
        Message<byte[]> message = MessageBuilder.withPayload("hello".getBytes(StandardCharsets.UTF_8)).build();

        Object resolved = resolver.resolveArgument(parameter("onPayload", String.class), message);

        assertEquals("hello", resolved);
    }

    @Test
    void payload_resolver_throws_when_conversion_fails() throws Exception {
        TitanPayloadHandlerMethodArgumentResolver resolver = new TitanPayloadHandlerMethodArgumentResolver(CONVERTER);
        Message<byte[]> message = MessageBuilder.withPayload("hello".getBytes(StandardCharsets.UTF_8)).build();

        assertThrows(
                MessageConversionException.class,
                () -> resolver.resolveArgument(parameter("onNumber", Integer.class), message)
        );
    }

    private static MethodParameter parameter(String name, Class<?> type) throws NoSuchMethodException {
        Method method = FixtureHandler.class.getDeclaredMethod(name, type);
        return new MethodParameter(method, 0);
    }

    private static StompFrame createFrame(String body) {
        return StompFrame.create(StompHeaders.create(), StompCommand.MESSAGE, body.getBytes(StandardCharsets.UTF_8));
    }

    static final class FixtureHandler {
        void onMessage(Message<byte[]> message) {}
        void onFrame(StompFrame frame) {}
        void onPayload(String payload) {}
        void onNumber(Integer number) {}
    }
}
