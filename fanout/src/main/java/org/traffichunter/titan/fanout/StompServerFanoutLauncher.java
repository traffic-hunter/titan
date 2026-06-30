package org.traffichunter.titan.fanout;

import java.util.Locale;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.bootstrap.Settings;
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
 * SEND handler -> DispatchGateway -> StompDispatchExporter
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
            final Settings settings,
            final String protocol,
            final String transport,
            final Map<String, String> protocolOptions,
            final ManagedServer managedServer
    ) {
        DispatchMode mode = resolveMode(protocolOptions);
        ManagedServerFanoutAdapter adapter = findAdapter(protocol, transport, protocolOptions, managedServer);
        if (adapter == null) {
            throw new IllegalStateException("No fanout adapter for protocol=" + protocol + ", transport=" + transport);
        }

        adapter.apply(
                protocol,
                transport,
                protocolOptions,
                managedServer,
                mode::dispatchGateway
        );
        log.info("Fanout launcher started fanout mode = {}, fanout server = {}", mode.getName(), managedServer.name());
    }

    @Override
    public void apply(
            final String protocol,
            final String transport,
            final Map<String, String> protocolOptions,
            final ManagedServer managedServer
    ) {
        apply(new Settings(null, null, null), protocol, transport, protocolOptions, managedServer);
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

    private static DispatchMode resolveMode(final Map<String, String> protocolOptions) {
        String raw = protocolOptions.getOrDefault(OPTION_FANOUT_MODE, "virtual");
        String normalized = raw.toLowerCase(Locale.ROOT).trim();
        return DispatchMode.resolveMode(normalized);
    }
}
