package org.traffichunter.titan.fanout;

import java.util.Locale;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.traffichunter.titan.core.spi.*;
import org.traffichunter.titan.fanout.exporter.StompFanoutExporter;

@Slf4j
@SuppressWarnings("unused")
public final class FanoutStompServerLauncher implements FanoutLauncher {

    private static final String OPTION_FANOUT_MODE = "fanout-mode";

    @Override
    public boolean supports(
            final String protocol,
            final String transport,
            final Map<String, String> protocolOptions,
            final ManagedServer managedServer
    ) {
        return managedServer instanceof StompManagedServer
                && "stomp".equalsIgnoreCase(protocol);
    }

    @Override
    public void apply(
            final String protocol,
            final String transport,
            final Map<String, String> protocolOptions,
            final ManagedServer managedServer
    ) {
        StompManagedServer stompManagedServer = (StompManagedServer) managedServer;
        FanoutMode mode = resolveMode(protocolOptions);
        FanoutGateway fanoutGateway = mode.fanoutGateway(new StompFanoutExporter(stompManagedServer.server().connection()));
        StompSendToFanoutHandler stompSendToFanoutHandler = new StompSendToFanoutHandler(fanoutGateway);

        stompManagedServer.server().onStomp(handler ->
                handler.sendHandler(stompSendToFanoutHandler)
        );

        log.info("Fanout launcher started fanout mode = {}, fanout server = {}", mode.getName(), stompManagedServer.name());
    }

    private static FanoutMode resolveMode(final Map<String, String> protocolOptions) {
        String raw = protocolOptions.getOrDefault(OPTION_FANOUT_MODE, "virtual");
        String normalized = raw.toLowerCase(Locale.ROOT).trim();
        return FanoutMode.resolveMode(normalized);
    }
}
