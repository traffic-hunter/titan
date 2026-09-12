package org.traffichunter.titan.springframework.stomp.beans;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.traffichunter.titan.springframework.stomp.annotation.TitanListener;
import org.traffichunter.titan.springframework.stomp.listener.TitanListenerEndpoint;
import org.traffichunter.titan.springframework.stomp.listener.TitanListenerEndpointRegistry;

/**
 * Covers how a {@code @TitanListener} group reaches the endpoint it describes.
 */
class TitanListenerGroupTest {

    @Test
    void a_named_group_reaches_the_endpoint() {
        TitanListenerEndpoint endpoint = discover(new MarketListener(), beanFactory());

        assertThat(endpoint.group()).isEqualTo("market");
        assertThat(endpoint.destination()).isEqualTo("/topic/price");
    }

    @Test
    void a_listener_without_a_group_lands_in_the_default_group() {
        TitanListenerEndpoint endpoint = discover(new PlainListener(), beanFactory());

        assertThat(endpoint.group()).isEqualTo("default");
    }

    @Test
    void a_property_reference_is_resolved_before_the_group_is_checked() {
        DefaultListableBeanFactory beanFactory = beanFactory();
        beanFactory.addEmbeddedValueResolver(
                value -> value.replace("${titan.group}", "notification"));

        TitanListenerEndpoint endpoint = discover(new ConfiguredListener(), beanFactory);

        assertThat(endpoint.group()).isEqualTo("notification");
    }

    @Test
    void a_placeholder_nothing_resolves_fails_the_listener() {
        TitanListenerAnnotationBeanPostProcessor processor = processor(beanFactory());

        assertThatThrownBy(() -> processor.postProcessAfterInitialization(new ConfiguredListener(), "listener"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid @TitanListener group");
    }

    @Test
    void a_malformed_group_fails_the_listener() {
        TitanListenerAnnotationBeanPostProcessor processor = processor(beanFactory());

        assertThatThrownBy(() -> processor.postProcessAfterInitialization(new BadGroupListener(), "listener"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid @TitanListener group");
    }

    private static TitanListenerEndpoint discover(Object bean, DefaultListableBeanFactory beanFactory) {
        TitanListenerAnnotationBeanPostProcessor processor = processor(beanFactory);
        processor.postProcessAfterInitialization(bean, "listener");

        assertThat(processor.endpoints()).hasSize(1);
        return processor.endpoints().getFirst();
    }

    private static TitanListenerAnnotationBeanPostProcessor processor(DefaultListableBeanFactory beanFactory) {
        TitanListenerAnnotationBeanPostProcessor processor =
                new TitanListenerAnnotationBeanPostProcessor(new TitanListenerEndpointRegistry());
        processor.setBeanFactory(beanFactory);
        return processor;
    }

    private static DefaultListableBeanFactory beanFactory() {
        return new DefaultListableBeanFactory();
    }

    static final class MarketListener {
        @TitanListener(group = "market", destination = "/topic/price")
        public void onPrice(String payload) {
        }
    }

    static final class PlainListener {
        @TitanListener(destination = "/topic/price")
        public void onPrice(String payload) {
        }
    }

    static final class ConfiguredListener {
        @TitanListener(group = "${titan.group}", destination = "/topic/price")
        public void onPrice(String payload) {
        }
    }

    static final class BadGroupListener {
        @TitanListener(group = "bad/name", destination = "/topic/price")
        public void onPrice(String payload) {
        }
    }
}
