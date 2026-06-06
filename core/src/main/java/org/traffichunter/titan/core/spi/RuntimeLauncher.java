package org.traffichunter.titan.core.spi;

import java.util.Comparator;
import java.util.List;
import java.util.ServiceLoader;
import org.traffichunter.titan.bootstrap.Settings;

public interface RuntimeLauncher {

    static List<RuntimeLauncher> load() {
        return ServiceLoader.load(RuntimeLauncher.class, RuntimeLauncher.class.getClassLoader())
                .stream()
                .map(ServiceLoader.Provider::get)
                .sorted(Comparator.comparingInt(RuntimeLauncher::order))
                .toList();
    }

    default int order() {
        return 0;
    }

    List<AutoCloseable> start(Settings settings, List<ManagedServer> managedServers);
}
