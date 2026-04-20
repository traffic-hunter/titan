package org.traffichunter.titan.springframework.stomp.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.traffichunter.titan.core.channel.EventLoopGroups;
import org.traffichunter.titan.core.transport.stomp.StompClient;
import org.traffichunter.titan.core.transport.stomp.option.StompClientOption;
import org.traffichunter.titan.springframework.stomp.TitanClientManager;
import org.traffichunter.titan.springframework.stomp.TitanProperties;
import org.traffichunter.titan.springframework.stomp.TitanTemplate;

@AutoConfiguration
@ConditionalOnClass({StompClient.class, EventLoopGroups.class})
@EnableConfigurationProperties(TitanProperties.class)
@ConditionalOnProperty(prefix = "titan.stomp.client", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TitanStompClientAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "titanStompClientEventLoopGroups")
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
    @ConditionalOnMissingBean
    public StompClient titanStompClient(
            EventLoopGroups titanStompClientEventLoopGroups,
            StompClientOption titanStompClientOption
    ) {
        return StompClient.open(titanStompClientEventLoopGroups, titanStompClientOption);
    }

    @Bean
    @ConditionalOnMissingBean
    public TitanClientManager titanClientManager(
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
