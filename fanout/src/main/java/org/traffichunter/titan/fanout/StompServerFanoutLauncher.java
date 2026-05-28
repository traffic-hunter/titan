package org.traffichunter.titan.fanout;

import java.util.Locale;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.spi.*;

/**
 * SPI launcher that installs fanout behavior into a managed STOMP server.
 *
 * <p>The bootstrap/core SPI discovers this class as a {@link FanoutLauncher}.
 * When the managed server is STOMP, the launcher creates a fanout gateway,
 * connects it to the server-side STOMP connection registry through
 * {@link StompServerFanoutLauncher}, and registers {@link StompSendToFanoutHandler}
 * as the SEND command ingress.</p>
 *
 * <pre>{@code
 * Titan bootstrap
 *      |
 *      v
 * Managed STOMP server
 *      |
 *      v
 * StompServerFanoutLauncher.apply(...)
 *      |
 *      v
 * SEND handler -> FanoutGateway -> StompFanoutExporter
 * }</pre>
 */
@Slf4j
@SuppressWarnings("unused")
public final class StompServerFanoutLauncher implements FanoutLauncher {

    private static final String OPTION_FANOUT_MODE = "fanout-mode";

    @Override
    public boolean supports(
            final String protocol,
            final String transport,
            final Map<String, String> protocolOptions,
            final ManagedServer managedServer
    ) {
        return "stomp".equalsIgnoreCase(protocol) && findAdapter(protocol, transport, protocolOptions, managedServer) != null;
    }

    @Override
    public void apply(
            final String protocol,
            final String transport,
            final Map<String, String> protocolOptions,
            final ManagedServer managedServer
    ) {
        FanoutMode mode = resolveMode(protocolOptions);
        ManagedServerFanoutAdapter adapter = findAdapter(protocol, transport, protocolOptions, managedServer);
        if (adapter == null) {
            throw new IllegalStateException("No fanout adapter for protocol=" + protocol + ", transport=" + transport);
        }

        adapter.apply(protocol, transport, protocolOptions, managedServer, mode::fanoutGateway);
        log.info("Fanout launcher started fanout mode = {}, fanout server = {}", mode.getName(), managedServer.name());
    }

    private static @Nullable ManagedServerFanoutAdapter findAdapter(
            final String protocol,
            final String transport,
            final Map<String, String> protocolOptions,
            final ManagedServer managedServer
    ) {
        for (ManagedServerFanoutAdapter adapter : ManagedServerFanoutAdapter.load()) {
            if (adapter.supports(protocol, transport, protocolOptions, managedServer)) {
                return adapter;
            }
        }

        return null;
    }

    private static FanoutMode resolveMode(final Map<String, String> protocolOptions) {
        String raw = protocolOptions.getOrDefault(OPTION_FANOUT_MODE, "virtual");
        String normalized = raw.toLowerCase(Locale.ROOT).trim();
        return FanoutMode.resolveMode(normalized);
    }
}
