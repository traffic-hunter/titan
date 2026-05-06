package org.traffichunter.titan.core.spi;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * Hook for optional module integrations, such as fanout.
 *
 * <p>Launchers are loaded after a server has been created. They can inspect the selected
 * protocol and transport, then attach sidecar behavior without creating a direct dependency
 * from core bootstrap code to optional modules.</p>
 */
public interface FanoutLauncher {

    static List<FanoutLauncher> load() {
        return ServiceLoader.load(FanoutLauncher.class, FanoutLauncher.class.getClassLoader())
                .stream()
                .map(ServiceLoader.Provider::get)
                .sorted(Comparator.comparingInt(FanoutLauncher::order))
                .toList();
    }

    default int order() {
        return 0;
    }

    default boolean supports(
            final String protocol,
            final String transport,
            final Map<String, String> protocolOptions,
            final ManagedServer managedServer
    ) {
        return true;
    }

    void apply(
            String protocol,
            String transport,
            Map<String, String> protocolOptions,
            ManagedServer managedServer
    );
}
