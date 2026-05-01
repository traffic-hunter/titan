package org.traffichunter.titan.springframework.stomp.listener;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.MethodParameter;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.invocation.HandlerMethodArgumentResolver;
import org.springframework.messaging.handler.invocation.HandlerMethodArgumentResolverComposite;
import org.springframework.util.ErrorHandler;
import org.traffichunter.titan.core.channel.stomp.StompClientConnection;
import org.traffichunter.titan.core.codec.stomp.StompCommand;
import org.traffichunter.titan.core.codec.stomp.StompFrame;
import org.traffichunter.titan.core.codec.stomp.StompHeaders;
import org.traffichunter.titan.core.transport.stomp.StompClient;
import org.traffichunter.titan.core.util.Handler;
import org.traffichunter.titan.springframework.stomp.TitanClientManager;
import org.traffichunter.titan.springframework.stomp.TitanProperties;
import org.traffichunter.titan.springframework.stomp.factory.SimpleTitanListenerContainerFactory;

class TitanListenerContainerFactoryTest {

    private StompClientConnection connection;
    private TitanClientManager manager;

    @BeforeEach
    void setUp() {
        StompClient stompClient = mock(StompClient.class);
        connection = mock(StompClientConnection.class);
        manager = new TitanClientManager(stompClient, new TitanProperties());

        when(stompClient.connection()).thenReturn(connection);
        when(connection.isConnected()).thenReturn(true);
    }

    @Test
    void simple_factory_creates_container_with_configured_components() throws Exception {
        SimpleTitanListenerContainerFactory factory = new SimpleTitanListenerContainerFactory();
        HandlerMethodArgumentResolver resolver = new StringArgumentResolver();
        AtomicBoolean errorHandled = new AtomicBoolean(false);

        factory.setArgumentResolver(resolver);
        factory.setListenerErrorHandler(error -> errorHandled.set(true));

        TitanListenerEndpoint endpoint = endpoint();

        TitanListenerContainer container = factory.create(endpoint, manager);

        assertSame(endpoint, container.endpoint());
        assertSame(manager, container.manager());
        assertSame(resolver, firstResolver(container.argumentResolvers()));

        container.listenerErrorHandler().handleError(new IllegalStateException("boom"));
        assertTrue(errorHandled.get());
    }

    @Test
    void set_argument_resolvers_replaces_existing_resolvers() throws Exception {
        SimpleTitanListenerContainerFactory factory = new SimpleTitanListenerContainerFactory();

        factory.setArgumentResolver(new IntegerArgumentResolver());
        factory.setArgumentResolvers(new StringArgumentResolver());

        TitanListenerContainer container = factory.create(endpoint(), manager);

        assertTrue(container.argumentResolvers().supportsParameter(parameter("handle", String.class)));
        assertFalse(container.argumentResolvers().supportsParameter(parameter("handleNumber", Integer.class)));
    }

    @Test
    void listener_container_acks_message_after_successful_listener_invocation() throws Exception {
        TitanListenerContainer container = listenerContainer(endpoint("handle"));

        container.start();
        subscribedHandler().handle(messageFrame("msg-1"));

        verify(connection).ack("msg-1");
        verify(connection, never()).nack(anyString());
    }

    @Test
    void listener_container_nacks_message_after_failed_listener_invocation() throws Exception {
        AtomicBoolean errorHandled = new AtomicBoolean(false);
        TitanListenerContainer container = listenerContainer(
                endpoint("fail"),
                error -> errorHandled.set(true)
        );

        container.start();
        subscribedHandler().handle(messageFrame("msg-2"));

        assertTrue(errorHandled.get());
        verify(connection).nack("msg-2");
        verify(connection, never()).ack(anyString());
    }

    private static TitanListenerEndpoint endpoint() throws NoSuchMethodException {
        return endpoint("handle");
    }

    private static TitanListenerEndpoint endpoint(String name) throws NoSuchMethodException {
        Method method = Fixture.class.getDeclaredMethod(name, String.class);
        return new TitanListenerEndpoint("fixture#handle", "/topic/test", new Fixture(), method, "client", 1);
    }

    private TitanListenerContainer listenerContainer(TitanListenerEndpoint endpoint) {
        return listenerContainer(endpoint, error -> { });
    }

    private TitanListenerContainer listenerContainer(
            TitanListenerEndpoint endpoint,
            ErrorHandler listenerErrorHandler
    ) {
        HandlerMethodArgumentResolverComposite resolvers = new HandlerMethodArgumentResolverComposite();
        resolvers.addResolver(new StringArgumentResolver());
        return new TitanListenerContainer(endpoint, manager, resolvers, listenerErrorHandler);
    }

    @SuppressWarnings("unchecked")
    private Handler<StompFrame> subscribedHandler() {
        ArgumentCaptor<Handler<StompFrame>> captor = ArgumentCaptor.forClass(Handler.class);
        verify(connection).subscribe(eq("/topic/test"), captor.capture());
        return captor.getValue();
    }

    private static StompFrame messageFrame(String messageId) {
        StompHeaders headers = StompHeaders.create();
        headers.put(StompHeaders.Elements.MESSAGE_ID, messageId);
        return StompFrame.create(headers, StompCommand.MESSAGE, "hello".getBytes(StandardCharsets.UTF_8));
    }

    private static MethodParameter parameter(String name, Class<?> type) throws NoSuchMethodException {
        Method method = Fixture.class.getDeclaredMethod(name, type);
        return new MethodParameter(method, 0);
    }

    private static Object firstResolver(HandlerMethodArgumentResolverComposite composite) throws Exception {
        Method resolvers = HandlerMethodArgumentResolverComposite.class.getDeclaredMethod("getResolvers");
        resolvers.setAccessible(true);
        return ((java.util.List<?>) resolvers.invoke(composite)).getFirst();
    }

    private static final class StringArgumentResolver implements HandlerMethodArgumentResolver {
        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.getParameterType() == String.class;
        }

        @Override
        public Object resolveArgument(@NonNull MethodParameter parameter, @NonNull Message<?> message) {
            return "resolved";
        }
    }

    private static final class IntegerArgumentResolver implements HandlerMethodArgumentResolver {
        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.getParameterType() == Integer.class;
        }

        @Override
        public Object resolveArgument(@NonNull MethodParameter parameter, @NonNull Message<?> message) {
            return 1;
        }
    }

    static final class Fixture {
        void handle(String payload) {
        }

        void handleNumber(Integer payload) {
        }

        void fail(String payload) {
            throw new IllegalStateException("failed");
        }
    }
}
