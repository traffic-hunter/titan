package org.traffichunter.titan.springframework.stomp.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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
import org.traffichunter.titan.core.codec.stomp.StompCommand;
import org.traffichunter.titan.core.codec.stomp.StompFrame;
import org.traffichunter.titan.core.codec.stomp.StompFrames;
import org.traffichunter.titan.core.codec.stomp.StompHeaders;
import org.traffichunter.titan.client.TitanClient;
import org.traffichunter.titan.core.util.Handler;
import org.traffichunter.titan.springframework.stomp.core.TitanClientManager;
import org.traffichunter.titan.springframework.stomp.TitanProperties;
import org.traffichunter.titan.springframework.stomp.factory.SimpleTitanListenerContainerFactory;

class TitanListenerContainerFactoryTest {

    private static final String SUBSCRIPTION_ID = "sub-1";

    private TitanClient client;
    private TitanClientManager manager;

    @BeforeEach
    void setUp() {
        client = mock(TitanClient.class);
        manager = new TitanClientManager(client, new TitanProperties());

        when(client.isConnected()).thenReturn(true);
        when(client.subscribe(
                eq("default"),
                eq("/topic/test"),
                org.mockito.ArgumentMatchers.<Handler<StompFrames>>any()
        )).thenReturn(CompletableFuture.completedFuture(SUBSCRIPTION_ID));
        when(client.unsubscribe(anyString()))
                .thenReturn(CompletableFuture.completedFuture(mock(StompFrames.class)));
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

        verify(client).ack("msg-1");
        verify(client, never()).nack(anyString());
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
        verify(client).nack("msg-2");
        verify(client, never()).ack(anyString());
    }

    @Test
    void listener_container_stops_by_unsubscribing_its_own_subscription_id() throws Exception {
        TitanListenerContainer container = listenerContainer(endpoint("handle"));

        container.start();
        assertEquals(SUBSCRIPTION_ID, container.subscriptionId());

        container.stop();

        verify(client).unsubscribe(SUBSCRIPTION_ID);
        assertTrue(container.isStopped());
        assertNull(container.subscriptionId());
    }

    @Test
    void listener_container_subscribes_within_its_endpoint_group() throws Exception {
        when(client.subscribe(
                eq("market"),
                eq("/topic/test"),
                org.mockito.ArgumentMatchers.<Handler<StompFrames>>any()
        )).thenReturn(CompletableFuture.completedFuture("sub-market"));
        TitanListenerContainer container = listenerContainer(groupedEndpoint("market"));

        container.start();

        verify(client).subscribe(eq("market"), eq("/topic/test"), any());
        assertEquals("sub-market", container.subscriptionId());
    }

    @Test
    void listener_container_unsubscribes_even_while_the_connection_is_down() throws Exception {
        TitanListenerContainer container = listenerContainer(endpoint("handle"));
        container.start();

        when(client.isConnected()).thenReturn(false);
        when(client.unsubscribe(SUBSCRIPTION_ID))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("not connected")));

        container.stop();

        // The client drops the logical subscription, so a reconnect does not restore it.
        verify(client).unsubscribe(SUBSCRIPTION_ID);
        assertTrue(container.isStopped());
    }

    @Test
    void stopped_listener_ignores_a_late_delivery() throws Exception {
        TitanListenerContainer container = listenerContainer(endpoint("handle"));

        container.start();
        Handler<StompFrames> handler = subscribedHandler();
        container.stop();
        handler.handle(messageFrame("msg-3"));

        verify(client, never()).ack(anyString());
        verify(client, never()).nack(anyString());
    }

    @Test
    void listener_container_resets_running_when_subscribe_fails() throws Exception {
        when(client.subscribe(
                eq("default"),
                eq("/topic/test"),
                org.mockito.ArgumentMatchers.<Handler<StompFrames>>any()
        )).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("subscribe failed")));
        TitanListenerContainer container = listenerContainer(endpoint("handle"));

        assertThatThrownBy(container::start)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to start listener");
        assertTrue(container.isStopped());
    }

    @Test
    void listener_container_releases_a_subscription_that_arrives_after_stop() throws Exception {
        CompletableFuture<String> subscribing = new CompletableFuture<>();
        CountDownLatch subscribed = new CountDownLatch(1);
        when(client.subscribe(
                eq("default"),
                eq("/topic/test"),
                org.mockito.ArgumentMatchers.<Handler<StompFrames>>any()
        )).thenAnswer(invocation -> {
            subscribed.countDown();
            return subscribing;
        });
        TitanListenerContainer container = listenerContainer(endpoint("handle"));

        Thread starting = new Thread(container::start, "listener-start");
        starting.start();
        assertTrue(subscribed.await(5, TimeUnit.SECONDS));

        // Stopped before the SUBSCRIBE came back, so stop() has no identifier to work with. The
        // subscription the server is about to confirm is the start's to give back.
        container.stop();
        subscribing.complete(SUBSCRIPTION_ID);
        starting.join(5_000);

        verify(client).unsubscribe(SUBSCRIPTION_ID);
        assertTrue(container.isStopped());
        assertNull(container.subscriptionId());
    }

    @Test
    void listener_container_releases_a_subscription_that_arrives_after_the_start_timeout() throws Exception {
        TitanProperties properties = new TitanProperties();
        properties.setConnectTimeoutMillis(50L);
        manager = new TitanClientManager(client, properties);
        CompletableFuture<String> subscribing = new CompletableFuture<>();
        when(client.subscribe(
                eq("default"),
                eq("/topic/test"),
                org.mockito.ArgumentMatchers.<Handler<StompFrames>>any()
        )).thenReturn(subscribing);
        TitanListenerContainer container = listenerContainer(endpoint("handle"));

        assertThatThrownBy(container::start)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to start listener");

        // The SUBSCRIBE outlived its own timeout. The server still created the subscription, and
        // nothing else is left holding its identifier.
        subscribing.complete(SUBSCRIPTION_ID);

        verify(client).unsubscribe(SUBSCRIPTION_ID);
        assertTrue(container.isStopped());
        assertNull(container.subscriptionId());
    }

    private static TitanListenerEndpoint endpoint() throws NoSuchMethodException {
        return endpoint("handle");
    }

    private static TitanListenerEndpoint endpoint(String name) throws NoSuchMethodException {
        Method method = Fixture.class.getDeclaredMethod(name, String.class);
        return new TitanListenerEndpoint("fixture#handle", "/topic/test", new Fixture(), method, "client", 1);
    }

    private static TitanListenerEndpoint groupedEndpoint(String group) throws NoSuchMethodException {
        Method method = Fixture.class.getDeclaredMethod("handle", String.class);
        return new TitanListenerEndpoint(
                "fixture#handle", group, "/topic/test", new Fixture(), method, "client", 1);
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
    private Handler<StompFrames> subscribedHandler() {
        ArgumentCaptor<Handler<StompFrames>> captor = ArgumentCaptor.forClass(Handler.class);
        verify(client).subscribe(eq("default"), eq("/topic/test"), captor.capture());
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
