package org.traffichunter.titan.springframework.stomp.autoconfigure;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.traffichunter.titan.core.channel.EventLoopGroups;
import org.traffichunter.titan.core.transport.stomp.VertxStompClient;
import org.traffichunter.titan.core.transport.stomp.client.StompClient;
import org.traffichunter.titan.core.transport.stomp.client.StompClientProvider;
import org.traffichunter.titan.core.transport.stomp.client.TitanStompClientProvider;
import org.traffichunter.titan.core.transport.stomp.client.VertxStompClientProvider;
import org.traffichunter.titan.core.transport.stomp.option.StompClientOption;
import org.traffichunter.titan.springframework.stomp.TitanClientManager;
import org.traffichunter.titan.springframework.stomp.TitanProperties;
import org.traffichunter.titan.springframework.stomp.TitanTemplate;

import java.util.List;

/**
 * Autoconfiguration for Titan's Spring STOMP client integration.
 * Creates the client event loops, options, manager, and template.
 * User-defined beans can override each default component.
 *
 * @author yun
 */
@AutoConfiguration
@ConditionalOnClass({StompClient.class, EventLoopGroups.class})
@EnableConfigurationProperties(TitanProperties.class)
@ConditionalOnProperty(prefix = "spring.titan", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TitanStompClientAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "titanStompClientEventLoopGroups")
    @ConditionalOnProperty(prefix = "spring.titan", name = "client", havingValue = "titan", matchIfMissing = true)
    public EventLoopGroups titanStompClientEventLoopGroups(TitanProperties properties) {
        return EventLoopGroups.group(properties.getPrimaryThreads(), properties.getSecondaryThreads());
    }

    @Bean
    @ConditionalOnMissingBean
    public StompClientOption titanStompClientOption(TitanProperties properties) {
        return StompClientOption.builder()
                .host(properties.getHost())
                .port(properties.getPort())
                .login(properties.getLogin())
                .passcode(properties.getPasscode())
                .virtualHost(properties.getVirtualHost())
                .heartbeatX(properties.getHeartbeatX())
                .heartbeatY(properties.getHeartbeatY())
                .maxFrameLength(properties.getMaxFrameLength())
                .autoComputeContentLength(properties.isAutoComputeContentLength())
                .useStompFrame(properties.isUseStompFrame())
                .bypassHostHeader(properties.isBypassHostHeader())
                .build();
    }

    @Bean
    @ConditionalOnMissingBean(name = "titanStompClientProvider")
    public StompClientProvider titanStompClientProvider(ObjectProvider<EventLoopGroups> titanStompClientEventLoopGroups) {
        return new TitanStompClientProvider(titanStompClientEventLoopGroups::getObject);
    }

    @Bean
    @ConditionalOnClass(VertxStompClient.class)
    @ConditionalOnMissingBean(name = "vertxStompClientProvider")
    public StompClientProvider vertxStompClientProvider() {
        return new VertxStompClientProvider();
    }

    @Bean
    @ConditionalOnMissingBean
    public StompClient titanStompClient(
            List<StompClientProvider> stompClientProviders,
            StompClientOption titanStompClientOption,
            TitanProperties properties
    ) {
        return stompClientProviders.stream()
                .filter(provider -> provider.supports(properties.getClient(), titanStompClientOption.stompVersion().getVersion()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No STOMP client provider found for client: "
                        + properties.getClient()
                        + ", version: "
                        + titanStompClientOption.stompVersion().getVersion()))
                .create(titanStompClientOption);
    }

    @Bean
    @ConditionalOnMissingBean
    public TitanClientManager titanStompClientManager(
            StompClient titanStompClient,
            TitanProperties properties
    ) {
        return new TitanClientManager(titanStompClient, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public TitanTemplate titanStompTemplate(TitanClientManager titanClientManager) {
        return new TitanTemplate(titanClientManager);
    }
}
